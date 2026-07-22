package com.hrushi.firstakka.domain.events;

import java.util.List;

import com.hrushi.firstakka.domain.model.OrderState.OrderLine;

public sealed interface OrderEvents {

    record orderPlaced(
        String orderId,
        String customerId,
        List<OrderLine> lines,
        long totalCents
    ) implements OrderEvents {}

    record inventoryReserved(String orderId) implements OrderEvents {}
    record paymentRecorded(String orderId) implements OrderEvents {}
    record orderShipped(String orderId) implements OrderEvents {}
    record orderDelivered(String orderId) implements OrderEvents {}
    record orderFailed(String orderId, String reason) implements OrderEvents {}
    record orderCancelled(String orderId) implements OrderEvents {}
}
