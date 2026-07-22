package com.hrushi.firstakka.domain.commands;

public final class PaymentCommands {

    public record RecordPayment(String orderId, long amountCents) {}
}
