package com.hrushi.firstakka.domain.entities;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

import com.hrushi.firstakka.domain.commands.CustomerCommands.CreateCustomer;
import com.hrushi.firstakka.domain.commands.CustomerCommands.DeleteCustomer;
import com.hrushi.firstakka.domain.events.CustomerEvent;
import com.hrushi.firstakka.domain.events.CustomerEvent.customerCreated;
import com.hrushi.firstakka.domain.events.CustomerEvent.customerDeleted;
import com.hrushi.firstakka.domain.model.CustomerState;

@ComponentId("Customer")
public class CustomerEntity extends EventSourcedEntity<CustomerState, CustomerEvent>{

  @Override
  public CustomerState emptyState(){
    return CustomerState.empty();
  }

  public Effect<String> create(CreateCustomer cmd) {

      if (currentState().customerId() != null) {
        return effects().error("Customer already exists");
      }

      String customerId = commandContext().entityId();
      var event = new customerCreated(customerId, cmd.firstName(), cmd.lastName());
            
      

    return effects()
      .persist(event)
      .thenReply(__ -> customerId);

  }

  public Effect<String> delete(DeleteCustomer cmd) {
    if (currentState().customerId() == null) {
        return effects().error("Customer id does not exists");
    }
    if (currentState().deleted()) {
        return effects().error("Customer is already deleted.");
    }
    var id = currentState().customerId();
    var firstName = currentState().FirstName();
    var lastName = currentState().LastName();
    var event = new customerDeleted(id, firstName, lastName);

    return effects().persist(event).thenReply(__ -> id);
  }

  @Override
  public CustomerState applyEvent(CustomerEvent event) {
    
    return switch (event) {
      case customerCreated e -> new CustomerState( e.customerId(), e.FirstName(), e.LastName(), false);
      case customerDeleted e -> new CustomerState ( e.customerId(), e.FirstName(), e.LastName(), true);
    };
  }  
}