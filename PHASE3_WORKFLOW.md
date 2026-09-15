# Phase 3 Workflow — Checkout and Orders

This workflow is for implementing Phase 3 of `buy02Plan.md` on the branch
`feature-phase3-orders-checkout`.

The work extends the existing `order-service`; it does not create another
microservice. It also adds the minimum stock-management API to
`product-service` and connects the completed backend flows to the Angular
frontend.

## 1. Phase 3 outcome

A buyer must be able to:

1. Add products to the persistent cart.
2. Submit a shipping address and payment method.
3. Check out a cart containing products from multiple sellers.
4. Receive one order per seller, grouped by one checkout ID.
5. View order history and order details.
6. Cancel an eligible order and restore its stock.
7. Reorder an existing order into the current cart.

A seller must be able to:

1. View only orders belonging to that seller.
2. Search/filter their orders.
3. Move orders through legal status transitions only.

All protected behavior must enforce JWT ownership and role rules.

## 2. Communication rules

Use each communication mechanism for the type of consistency it provides.

### Synchronous REST: stock correctness

`order-service` calls `product-service` directly for operations that must
succeed before the order operation can succeed:

- Validate the current product, seller, price, and stock during checkout.
- Atomically decrement stock during checkout.
- Atomically restore stock during cancellation.

The product update must be an atomic conditional MongoDB operation so two
concurrent checkouts cannot reduce stock below zero.

Expected failures:

- Insufficient stock or stale product data: `409 Conflict`.
- Product service unavailable: `503 Service Unavailable`.
- Invalid product or ownership data: the service's existing safe error shape.

### Kafka: asynchronous order events

After the synchronous operation succeeds, `order-service` publishes events:

- `order.created`
- `order.status.changed`
- `order.cancelled`

Kafka events are for audit, notifications, analytics, and future consumers.
The Angular frontend does not connect to Kafka; it reads current state through
the Gateway's HTTP APIs.

If event publishing is retried or fails, the failure must be handled according
to the existing Kafka conventions. Do not pretend that an event was published
successfully.

## 3. Implementation sequence

### Step 0 — Establish the baseline

- Confirm the branch is `feature-phase3-orders-checkout`.
- Confirm the working tree is clean or record any intentional changes.
- Run the existing `order-service` and `product-service` tests.
- Read the existing cart model, service, controller, security, exception
  handler, and `Product` update patterns before editing.

Checkpoint: the current test baseline passes and the existing cart behavior is
understood.

### Step 1 — Add the order domain model

In `order-service`:

- Add `Order`.
- Add embedded order item snapshot data.
- Add shipping-address data.
- Add status and payment enums.
- Add status-history entries.
- Add repository queries for buyer and seller ownership.
- Add indexes for buyer, seller, checkout group, and seller/status lookup.

Required order invariants:

- Each order belongs to exactly one buyer and one seller.
- Product name, price, and image data are snapshotted into the order.
- Subtotal is calculated server-side.
- New orders start in `PENDING`.
- The initial status-history entry is recorded.
- Versioning supports optimistic locking.

Add DTOs for request and response data. Never expose persistence internals or
trust client-supplied price, seller, subtotal, status, or payment status.

Checkpoint: order serialization, repository queries, and domain unit tests pass.

### Step 2 — Add atomic product stock adjustment

In `product-service`:

- Add an internal stock-adjustment request/response contract.
- Add an endpoint such as `PATCH /products/{id}/stock`.
- Accept a positive or negative stock delta as appropriate.
- Reject a decrement that would make stock negative.
- Use an atomic conditional update.
- Keep this endpoint off the public Gateway route unless an internal route is
  explicitly required.
- Apply the existing JWT, correlation-ID, validation, and error conventions.

In `order-service`:

- Add or extend `ProductClient`.
- Configure the product-service base URL and REST timeouts.
- Map product-service failures to explicit order-service exceptions.

Checkpoint: concurrent/insufficient-stock tests prove that stock cannot become
negative and service failures are not silently ignored.

### Step 3 — Implement checkout

Add `POST /orders/checkout` to `order-service`.

Processing order:

1. Authenticate the buyer and read the buyer's cart.
2. Reject an empty cart with a clear client error.
3. Re-read every product from `product-service`.
4. Validate product existence, current seller, current price, and requested
   quantity.
5. Group cart lines by seller.
6. Atomically decrement stock for every line.
7. If a later line fails, restore all earlier successful decrements.
8. Create one order per seller using one shared `checkoutGroupId`.
9. Store snapshots and calculate each seller order subtotal.
10. Clear the cart only after order creation succeeds.
11. Publish one `order.created` event per created order.
12. Return the created order group or equivalent response DTO.

The implementation must not create an order for stock that was not reserved.
Do not trust cart price snapshots as authoritative checkout prices.

Checkpoint: tests cover empty carts, multiple sellers, stale prices,
insufficient stock, product-service outage, rollback after a partial
reservation, cart clearing, snapshots, and event publication.

### Step 4 — Implement order reads and ownership masking

Add:

- `GET /orders/mine`
- `GET /orders/selling`
- `GET /orders/{id}`

Rules:

- Buyers see only orders whose `buyerId` equals the JWT subject.
- Sellers see only orders whose `sellerId` equals the JWT subject.
- A user who does not own an order receives `404`, not `403`, to preserve
  existence masking.
- Seller listing requires the `SELLER` role.
- Add pagination and the planned optional search/status parameters only where
  they are supported by the current model.

Checkpoint: buyer, seller, wrong-role, and cross-owner tests pass.

### Step 5 — Implement status transitions

Add `PATCH /orders/{id}/status`.

Allowed transitions:

```text
PENDING → CONFIRMED → SHIPPED → DELIVERED
PENDING → CANCELLED
CONFIRMED → CANCELLED
SHIPPED → CANCELLED
```

Confirm the exact cancellation policy before implementation if the business
rules need to differ. No transition may skip a required state, change a
terminal state, or be performed by a non-owning seller.

For every valid transition:

- Update the status.
- Append `{status, changedAt, changedBy}` to status history.
- Update payment status when delivery rules require it.
- Publish `order.status.changed`.

Checkpoint: every legal and illegal transition is covered by unit tests, and
seller authorization is enforced.

### Step 6 — Implement cancellation and stock restoration

Add `POST /orders/{id}/cancel`.

Rules:

- Buyer or owning seller may cancel an order before `DELIVERED`.
- A cancelled order cannot be cancelled twice.
- Restore each order item's quantity synchronously through
  `product-service`.
- Mark the order `CANCELLED` only when restoration succeeds.
- Append the status-history entry.
- Publish `order.cancelled`.

Use a safe strategy for repeated requests so stock is not restored twice.
Document and test the behavior if stock restoration partially fails.

Checkpoint: cancellation authorization, terminal-state rejection, stock
restoration, idempotency/retry behavior, and Kafka publication are tested.

### Step 7 — Implement reorder

Add `POST /orders/{id}/reorder`.

Rules:

- Only the buyer who owns the order may reorder it.
- Add available items to the buyer's current cart.
- Re-fetch current product data and use current prices and stock.
- Do not copy old order prices into the new cart.
- Return the updated cart and clearly report unavailable products.

Checkpoint: reorder uses current product data and does not mutate the
historical order.

### Step 8 — Add frontend HTTP services and checkout

In Angular:

- Add typed order and checkout interfaces.
- Add `OrderService` under `frontend/src/app/shared/services`.
- Add a checkout feature/page.
- Reuse the existing auth interceptor, guards, reactive forms, and validation
  patterns.
- Show inline validation errors for address and payment fields.
- Display `409` stock conflicts per affected product where possible.
- Display clear messages for `5xx`, timeout, and authentication failures.

Do not add Kafka client code to the frontend.

Checkpoint: an authenticated buyer can submit checkout through the Gateway
and see the resulting order response.

### Step 9 — Add buyer order pages

Add buyer order routes/pages for:

- Order history.
- Status filtering/search where supported.
- Order detail.
- Status timeline from `statusHistory`.
- Cancel action.
- Reorder action.

Protect routes with `AuthGuard`.

Checkpoint: a buyer can complete checkout, refresh the browser, view history,
open an order, cancel it, and reorder it.

### Step 10 — Add seller order management

Extend the existing seller feature:

- Add seller order listing.
- Add status controls limited to the next legal transition.
- Add search/filter controls.
- Show only the seller's own incoming orders.
- Handle `403`, `404`, `400`, and `409` responses consistently.

Protect seller routes with both `AuthGuard` and `RoleGuard`.

Checkpoint: two sellers cannot view or update each other's orders.

### Step 11 — Integration, CI, and documentation

- Add or update backend and frontend tests next to the affected code.
- Verify Gateway routes and internal product-service calls.
- Verify Kafka topic names and event payloads.
- Run the smallest relevant Maven and Angular test commands.
- Run the existing verification script if targeted tests pass.
- Run the existing build/Sonar workflow before opening the PR.
- Update `buy02Plan.md` checkboxes only for work actually completed.
- Document any design deviation, especially transaction compensation or event
  retry behavior.

## 4. Required acceptance scenario

Use two sellers, one buyer, and at least two products:

1. Register/login both sellers and the buyer.
2. Create products with known stock and different sellers.
3. Add products from both sellers to the buyer's cart.
4. Confirm the cart survives a browser refresh.
5. Check out with cash on delivery.
6. Confirm two orders were created with one shared `checkoutGroupId`.
7. Confirm stock decreased by the ordered quantities.
8. Confirm each seller sees only their own order.
9. Move each order through valid status transitions.
10. Attempt an invalid skipped transition and confirm `400`.
11. Cancel one eligible order.
12. Confirm its stock was restored exactly once.
13. Confirm the buyer cannot access another buyer's order and receives `404`.
14. Reorder the cancelled order and confirm current prices are used.
15. Confirm `order.created`, `order.status.changed`, and `order.cancelled`
    events are published with correlation/order identifiers.

## 5. Definition of done

Phase 3 is complete only when:

- Backend checkout, orders, status transitions, cancellation, and reorder work.
- Stock decrement and restoration are atomic and tested.
- Kafka events are published for order lifecycle changes.
- Buyer and seller Angular workflows are connected through the Gateway.
- Ownership and role rules are enforced.
- Error responses use the established JSON error shape.
- Unit tests and the relevant frontend tests pass.
- The acceptance scenario passes end to end.
- CI/SonarQube passes on the feature PR.
- The Phase 3 status in `buy02Plan.md` is updated accurately.
