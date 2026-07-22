package com.hrushi.firstakka.domain.commands;

import com.hrushi.firstakka.domain.model.OrderState.OrderLine;
import java.util.List;

public final class OrderCommands {

    public record PlaceOrder(
        String customerId,
        List<OrderLine> lines
    ) {}

    public record MarkReserved() {}
    public record MarkPaid() {}
    public record MarkShipped() {}
    public record MarkDelivered() {}
    public record MarkFailed(String reason) {}
    public record CancelIfNotPaid() {}
}
