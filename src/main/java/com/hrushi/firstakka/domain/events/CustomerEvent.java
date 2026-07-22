package com.hrushi.firstakka.domain.events;

public sealed interface CustomerEvent {
    
    public record customerCreated(
        String customerId,
        String FirstName,
        String LastName
    ) implements CustomerEvent {}

    public record customerDeleted(
        String customerId,
        String FirstName,
        String LastName
    ) implements CustomerEvent {}

}
