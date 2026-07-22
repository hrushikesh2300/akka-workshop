package com.hrushi.firstakka.domain.workflows;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;

import com.hrushi.firstakka.domain.commands.InventoryCommands.ReleaseInventory;
import com.hrushi.firstakka.domain.commands.InventoryCommands.ReserveInventory;
import com.hrushi.firstakka.domain.commands.OrderCommands.CancelIfNotPaid;
import com.hrushi.firstakka.domain.commands.OrderCommands.MarkDelivered;
import com.hrushi.firstakka.domain.commands.OrderCommands.MarkFailed;
import com.hrushi.firstakka.domain.commands.OrderCommands.MarkPaid;
import com.hrushi.firstakka.domain.commands.OrderCommands.MarkReserved;
import com.hrushi.firstakka.domain.commands.OrderCommands.MarkShipped;
import com.hrushi.firstakka.domain.commands.OrderCommands.PlaceOrder;
import com.hrushi.firstakka.domain.commands.PaymentCommands.RecordPayment;
import com.hrushi.firstakka.domain.entities.InventoryEntity;
import com.hrushi.firstakka.domain.entities.OrderEntity;
import com.hrushi.firstakka.domain.entities.PaymentEntity;
import com.hrushi.firstakka.domain.model.OrderState.OrderLine;

import java.time.Duration;
import java.util.List;

@ComponentId("order-workflow")
public class OrderWorkflow extends Workflow<OrderWorkflow.State> {

    public record State(
        String orderId,
        String customerId,
        List<OrderLine> lines,
        long totalCents
    ) {}

    public record StartOrder(String customerId, List<OrderLine> lines) {}

    private static final String INVENTORY_ID = "main-warehouse";
    private static final Duration ABANDON_TIMEOUT = Duration.ofSeconds(30);
    private static final String ABANDON_TIMER_PREFIX = "abandon-";

    private final ComponentClient client;

    public OrderWorkflow(ComponentClient client) {
        this.client = client;
    }

    @Override
    public WorkflowDef<State> definition() {
        return workflow()
            .addStep(reserveStep())
            .addStep(chargeStep())
            .addStep(shipStep())
            .addStep(deliverStep())
            .addStep(compensateStep());
    }

    public Effect<String> start(StartOrder cmd) {
        if (currentState() != null) {
            return effects().error("Workflow already started");
        }
        String orderId = commandContext().workflowId();
        long total = cmd.lines().stream()
            .mapToLong(l -> l.unitPriceCents() * l.qty())
            .sum();

        // Record the order in its own event-sourced entity.
        client.forEventSourcedEntity(orderId)
            .method(OrderEntity::place)
            .invoke(new PlaceOrder(cmd.customerId(), cmd.lines()));

        // Schedule an auto-cancel timer for abandoned carts.
        timers().createSingleTimer(
            ABANDON_TIMER_PREFIX + orderId,
            ABANDON_TIMEOUT,
            client.forEventSourcedEntity(orderId)
                .method(OrderEntity::cancelIfNotPaid)
                .deferred(new CancelIfNotPaid())
        );

        return effects()
            .updateState(new State(orderId, cmd.customerId(), cmd.lines(), total))
            .transitionTo("reserve")
            .thenReply(orderId);
    }

    private Step reserveStep() {
        return step("reserve")
            .call(() ->
                client.forKeyValueEntity(INVENTORY_ID)
                    .method(InventoryEntity::reserve)
                    .invoke(new ReserveInventory(currentState().orderId(), currentState().lines()))
            )
            .andThen(String.class, result -> {
                if ("OK".equals(result)) {
                    client.forEventSourcedEntity(currentState().orderId())
                        .method(OrderEntity::markReserved)
                        .invoke(new MarkReserved());
                    return effects().transitionTo("charge");
                }
                return effects().transitionTo("compensate", "inventory:" + result);
            });
    }

    private Step chargeStep() {
        return step("charge")
            .call(() -> {
                String result = client.forKeyValueEntity(currentState().orderId())
                    .method(PaymentEntity::charge)
                    .invoke(new RecordPayment(currentState().orderId(), currentState().totalCents()));
                // Cancel the abandon timer here (in the step action, not in andThen —
                // timer ops are only valid inside command handlers or step actions).
                if ("OK".equals(result)) {
                    timers().delete(ABANDON_TIMER_PREFIX + currentState().orderId());
                }
                return result;
            })
            .andThen(String.class, result -> {
                if ("OK".equals(result)) {
                    client.forEventSourcedEntity(currentState().orderId())
                        .method(OrderEntity::markPaid)
                        .invoke(new MarkPaid());
                    return effects().transitionTo("ship");
                }
                return effects().transitionTo("compensate", "payment:" + result);
            });
    }

    private Step shipStep() {
        return step("ship")
            .call(() -> {
                client.forEventSourcedEntity(currentState().orderId())
                    .method(OrderEntity::markShipped)
                    .invoke(new MarkShipped());
                return "SHIPPED";
            })
            .andThen(String.class, __ -> effects().transitionTo("deliver"));
    }

    private Step deliverStep() {
        return step("deliver")
            .call(() -> {
                // "blocked-customer" simulates an address the carrier can't reach,
                // giving this step a failure path to exercise like reserve/charge.
                if ("blocked-customer".equals(currentState().customerId())) {
                    return "ADDRESS_UNREACHABLE";
                }
                return "OK";
            })
            .andThen(String.class, result -> {
                if ("OK".equals(result)) {
                    client.forEventSourcedEntity(currentState().orderId())
                        .method(OrderEntity::markDelivered)
                        .invoke(new MarkDelivered());
                    return effects().end();
                }
                return effects().transitionTo("compensate", "delivery:" + result);
            });
    }

    private Step compensateStep() {
        return step("compensate")
            .call(String.class, reason -> {
                client.forKeyValueEntity(INVENTORY_ID)
                    .method(InventoryEntity::release)
                    .invoke(new ReleaseInventory(currentState().orderId()));

                client.forEventSourcedEntity(currentState().orderId())
                    .method(OrderEntity::markFailed)
                    .invoke(new MarkFailed(reason));

                timers().delete(ABANDON_TIMER_PREFIX + currentState().orderId());
                return reason;
            })
            .andThen(String.class, __ -> effects().end());
    }
}
