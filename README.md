# my-first-akka

A practice project built while learning the [Akka SDK](https://doc.akka.io) (Java, v3.4.6). It's a small order-processing
system used as a playground for the core Akka building blocks: **models, commands, events, entities, workflows, views,
and compensation (saga rollback)**.

## What it does

Customers can be created, set preferences, and place orders. Placing an order kicks off a workflow that reserves
inventory, charges payment, ships, and delivers the order — rolling back ("compensating") whatever already succeeded
if any step fails.

## Concepts covered

| Concept | Where | Notes |
|---|---|---|
| **Model** | `domain/model/*.java` | Plain records describing state shapes: `OrderState`, `OrderStatus`, `CustomerState`, `InventoryState`, `PaymentRecord`, `CustomerPreferences`. |
| **Command** | `domain/commands/*.java` | Input messages sent to entities/workflows, e.g. `PlaceOrder`, `MarkPaid`, `ReserveInventory`, `RecordPayment`. |
| **Event** | `domain/events/*.java` | Facts persisted by event-sourced entities, e.g. `orderPlaced`, `inventoryReserved`, `orderShipped`, `orderFailed`. |
| **Entity (event-sourced)** | `OrderEntity`, `CustomerEntity` | State is rebuilt by folding persisted events (`applyEvent`). Used where a full history of what happened matters. |
| **Entity (key-value)** | `InventoryEntity`, `PaymentEntity`, `CustomerPreferencesEntity` | State is stored and replaced directly — no event log. Used for simpler, current-state-only data. |
| **Workflow** | `OrderWorkflow` | Orchestrates the multi-step, multi-entity order process as a durable state machine (`reserve → charge → ship → deliver`), with timers for abandoned-cart cancellation. |
| **View** | `views/OrdersView.java`, `views/CustomerView.java` | Read-only, queryable projections built by consuming entity events (`@Consume.FromEventSourcedEntity`) into a table. |
| **Compensate** | `OrderWorkflow.compensateStep()` | If `reserve`, `charge`, or `deliver` fails, the workflow transitions to a `compensate` step that releases reserved inventory and marks the order failed — a saga-style rollback. |

## Order workflow

```
start
  │
  ▼
reserve ──fail──► compensate ──► end (order FAILED)
  │ ok
  ▼
charge ───fail──► compensate ──► end (order FAILED)
  │ ok
  ▼
ship
  │
  ▼
deliver ──fail──► compensate ──► end (order FAILED)
  │ ok
  ▼
end (order DELIVERED)
```

- A 30-second timer is scheduled on `start` to auto-cancel the order if payment hasn't completed (abandoned cart);
  it's cancelled once payment succeeds.
- `deliver` deliberately fails for `customerId == "blocked-customer"`, giving a way to exercise the compensation
  path end-to-end.
- Compensation releases the inventory reservation and persists an `orderFailed` event, which flows into `OrdersView`
  so failed orders show up with a `reason`.

## API

All endpoints are under `/api` (see `api/*.java`).

**Customers**
- `POST /customers` — create a customer
- `DELETE /customers/{customerId}` — delete a customer
- `GET /customers` — list customers (via `CustomerView`)

**Preferences**
- `PUT /customers/{customerId}/preferences` — set preferences
- `GET /customers/{customerId}/preferences` — get preferences

**Orders**
- `POST /orders` — place an order, starts `OrderWorkflow`
- `GET /orders` — list all orders (via `OrdersView`)
- `GET /orders/customer/{customerId}` — list orders for a customer
- `GET /orders/{orderId}` — get a single order's current state

## Running locally

```bash
mvn compile exec:java
```

The Akka SDK local runtime starts on `http://localhost:9000` by default.

Example: place an order
```bash
curl -X POST http://localhost:9000/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"cust-1","lines":[{"sku":"widget","qty":2,"unitPriceCents":500}]}'
```

## Tech

- Java 21
- Akka SDK (`akka-javasdk-parent` 3.4.6)
- Maven
