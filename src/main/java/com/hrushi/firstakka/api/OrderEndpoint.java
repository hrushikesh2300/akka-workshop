package com.hrushi.firstakka.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;

import com.hrushi.firstakka.domain.entities.OrderEntity;
import com.hrushi.firstakka.domain.model.OrderState;
import com.hrushi.firstakka.domain.model.OrderState.OrderLine;
import com.hrushi.firstakka.domain.workflows.OrderWorkflow;
import com.hrushi.firstakka.domain.workflows.OrderWorkflow.StartOrder;
import com.hrushi.firstakka.views.OrdersView;
import com.hrushi.firstakka.views.OrdersView.OrderRows;

import java.util.List;
import java.util.UUID;

@HttpEndpoint("/api")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class OrderEndpoint {

    public record PlaceOrderRequest(String customerId, List<OrderLine> lines) {}
    public record PlaceOrderResponse(String orderId, String status) {}

    private final ComponentClient client;

    public OrderEndpoint(ComponentClient client) {
        this.client = client;
    }

    @Post("/orders")
    public PlaceOrderResponse place(PlaceOrderRequest req) {
        validate(req);
        String orderId = "order-" + UUID.randomUUID();
        String result = client.forWorkflow(orderId)
            .method(OrderWorkflow::start)
            .invoke(new StartOrder(req.customerId(), req.lines()));
        return new PlaceOrderResponse(result, "STARTED");
    }

    private void validate(PlaceOrderRequest req) {
        if (req.customerId() == null || req.customerId().isBlank()) {
            throw HttpException.badRequest("customerId is required");
        }
        if (req.lines() == null || req.lines().isEmpty()) {
            throw HttpException.badRequest("order must contain at least one line");
        }
        var seenSkus = new java.util.HashSet<String>();
        for (OrderLine line : req.lines()) {
            if (line.sku() == null || line.sku().isBlank()) {
                throw HttpException.badRequest("line sku is required");
            }
            if (line.qty() <= 0) {
                throw HttpException.badRequest("line qty must be positive: " + line.sku());
            }
            if (line.unitPriceCents() < 0) {
                throw HttpException.badRequest("line unitPriceCents cannot be negative: " + line.sku());
            }
            if (!seenSkus.add(line.sku())) {
                throw HttpException.badRequest("duplicate sku in order lines: " + line.sku());
            }
        }
    }

    @Get("/orders")
    public OrderRows list() {
        return client.forView().method(OrdersView::all).invoke();
    }

    @Get("/orders/customer/{customerId}")
    public OrderRows byCustomer(String customerId) {
        return client.forView().method(OrdersView::byCustomer).invoke(customerId);
    }

    @Get("/orders/{orderId}")
    public OrderState get(String orderId) {
        return client.forEventSourcedEntity(orderId)
            .method(OrderEntity::get)
            .invoke();
    }
}
