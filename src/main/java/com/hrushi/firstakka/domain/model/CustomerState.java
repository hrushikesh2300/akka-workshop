package com.hrushi.firstakka.domain.model;

public record CustomerState(
    String customerId,
    String FirstName,
    String LastName,
    boolean deleted
) {
    public static CustomerState empty () {
        return new CustomerState(null, null, null, false);
    }
}