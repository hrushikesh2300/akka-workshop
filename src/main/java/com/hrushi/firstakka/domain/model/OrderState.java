package com.hrushi.firstakka.domain.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderState(
    String orderId,
    String customerId,
    List<OrderLine> lines,
    long totalCents,
    OrderStatus status,
    String reason
) {
    public static OrderState empty() {
        return new OrderState(null, null, List.of(), 0L, null, null);
    }

    public OrderState withStatus(OrderStatus next) {
        return new OrderState(orderId, customerId, lines, totalCents, next, reason);
    }

    public OrderState withFailure(String reason) {
        return new OrderState(orderId, customerId, lines, totalCents, OrderStatus.FAILED, reason);
    }

    public record OrderLine(String sku, int qty, long unitPriceCents) {}
}
