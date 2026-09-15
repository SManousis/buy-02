# Buy-02 Plan — Completing the E-Commerce Platform

This document plans the buy-02 work on top of the existing buy-01 codebase (Discovery, Gateway, User, Product, Media services + Angular frontend, documented in [`README.md`](README.md)). It exists to be checked off against, not just read once — each section ends with what "done" looks like, and Section 12 maps every audit question in the brief to the section that answers it.

Nothing described here as already built has been re-verified in this pass except by reading source; treat "existing" claims the same way the current README treats its own audit status — confirmed by reading code, not by a runtime run.

## Implementation status and teammate handoff board

This is the shared source of truth for current progress. Update the relevant checkbox in the same PR as the implementation. A phase is complete only when its PR is approved, CI is green, and it is merged into `main`.

- `[x]` means completed and merged, except where a line explicitly says the work is prepared on an open branch.
- `[ ]` means action is still required.

### Phase 0 - Collaboration and quality infrastructure

- [x] Add GitHub alongside the existing Gitea remote.
- [x] Replace ngrok with a WSL2 self-hosted GitHub Actions runner for local SonarQube.
- [x] Protect `main` with PR, approval, and `build-and-analyze` requirements.
- [x] Configure and verify the Jenkins multibranch pipeline and email notifications.
- [x] Document the runner flow in `instructions.md` and `SONAR_QUICKSTART.md`.

### Phase 1 - Order-service foundation

- [x] Add `order-service` on port 8084 with MongoDB, Eureka, JWT security, CORS, correlation IDs, and health checks.
- [x] Add Gateway, Compose, Jenkins, SonarQube, verification, and staging wiring.
- [x] Merge `feature-order-service-skeleton` after CI and teammate approval.

### Phase 2 - Persistent cart

- [x] Implement the persistent cart model, unique user index, product/stock validation, snapshots, endpoints, error handling, and tests on `feature-cart-api`.
- [x] Verify the order-service suite: 7 tests passing.
- [x] Commit and push `feature-cart-api` to GitHub and Gitea.
- [x] Open the cart PR, pass Jenkins and SonarQube, obtain approval, and merge.
- [x] Implement the Angular cart page, cart service, controls, subtotal, and header badge on `feature-angular-cart`; production build passes.
- [ ] Push `feature-angular-cart`, pass Jenkins and SonarQube, obtain approval, and merge.
- [ ] Verify manually that cart contents and quantities survive browser refresh.

### Phase 3 - Checkout and orders

- [ ] Add the order schema, status history, per-seller checkout, and cash on delivery.
- [ ] Add atomic stock decrement and restore integration.
- [ ] Add buyer/seller listing, search, status transitions, cancellation, and reorder.
- [ ] Add backend tests and buyer/seller Angular pages.

### Phase 4 - Search and filtering

- [ ] Add product keyword, price, seller, stock, sorting, and pagination filters.
- [ ] Add buyer and seller order search/status filters.
- [ ] Add responsive Angular search/filter controls.

### Phase 5 - Profile analytics

- [ ] Add buyer top products, most-bought products, and total spent.
- [ ] Add seller best-selling products and total revenue.
- [ ] Add buyer and seller Angular analytics panels.

### Phase 6 - Completion and audit

- [ ] Complete responsive-design, validation, security, and negative-path testing.
- [ ] Record SonarQube findings and fixes.
- [ ] Update README architecture, routes, database design, Kafka topics, and runtime instructions.
- [ ] Run and document the Section 10 acceptance scenario.
- [ ] Confirm every feature PR has green CI and teammate approval.
- [ ] Implement wishlist and extra payment methods only after required work is complete (bonus).

### Teammate handoff procedure

1. Select the first unchecked implementation item whose dependencies are complete.
2. Run `git switch main` and `git pull github main`, then create a focused feature branch.
3. Add tests and update this board in the same PR.
4. Push to GitHub and Gitea; open the GitHub PR and wait for Jenkins and SonarQube.
5. Request review, resolve conversations, merge only when green, then synchronize Gitea `main`.


## 0. What buy-01 already gives us

Read from the current source, not assumed:

- **Services**: `discovery-service` (Eureka, 8761), `api-gateway` (Spring Cloud Gateway, 8080), `user-service` (8081), `product-service` (8082), `media-service` (8083). Each Java service is Spring Boot 3 / Java 21, registers with Eureka, validates the same HS256 JWT (`JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`), and exposes `/actuator/health`.
- **Data**: one Mongo database per service (`userservice`, `productservice`, `mediaservice`) on a shared `mongo:7` container. `User` has `id, username, email, password(bcrypt), role(CLIENT|SELLER), avatarMediaId, version, createdAt, updatedAt`. `Product` has `id, sellerId, name, description, price(BigDecimal), stock, imageIds[], version, createdAt, updatedAt`, with a unique partial index enforcing an image belongs to at most one product. `MediaAsset` has `id, sellerId, originalFileName, storedFileName, contentType, sizeBytes, storageKey, createdAt, updatedAt`.
- **Cross-service pattern**: services call each other directly by container hostname for synchronous ownership checks (e.g. `product-service` → `MEDIA_SERVICE_BASE_URL` to confirm a seller owns an image), and use Kafka for asynchronous cleanup (`product.deleted` → media deletes images; `image.deleted` → product strips the ID). This split — **synchronous direct call when a request must succeed-or-fail together, Kafka when it's best-effort cleanup** — is the precedent buy-02 should follow rather than inventing a new one.
- **Gateway routing**: one `RouteLocator` entry per service in `api-gateway/src/main/resources/application.yml`, matched by path prefix, resolved via `lb://<service-name>` through Eureka.
- **Frontend**: standalone-module Angular app (`auth`, `catalog`, `profile`, `seller`, `shared`, `layout`), with `AuthGuard`/`RoleGuard`, an auth token interceptor, and typed services (`auth`, `product`, `media`) under `shared/services`.
- **Security posture**: BCrypt passwords, JWT-only auth, `403` for wrong role, `404` for cross-owner access (existence-masked), global exception handlers producing a consistent JSON error shape, production HTTPS overlay with HSTS/CSP.
- **CI/CD**: `Jenkinsfile` builds+tests all 6 Maven modules and the Angular app in parallel, archives artifacts, and deploys to staging behind a flag. `.github/workflows/sonarqube.yml` runs backend+frontend SonarQube analysis on push/PR to `main`, gated as a required status check.
- **Buy-02 progress**: the `order-service` foundation is merged and the persistent cart API is prepared on `feature-cart-api`. Orders, wishlist, search/filtering, and buyer/seller analytics are not implemented yet. `PLAN.md` is still absent even though the README references it four times.

## 1. New architecture: `order-service`

Add one new Spring Boot module, `order-service`, port **8084**, database `orderservice`, following the exact skeleton of `product-service` (`client/`, `config/`, `controller/`, `dto/`, `exception/`, `kafka/`, `model/`, `repository/`, `security/`, `service/`).

It owns **carts, orders, and (bonus) wishlists** — everything that references a product but isn't the product itself. Rationale for putting cart and wishlist here rather than in `user-service`: both need seller/product/price snapshots and stock coordination, which is `order-service`'s job anyway; keeping them out of `user-service` keeps that service's scope at "identity and profile," matching its current size.

```text
Browser → Angular/Nginx → Gateway :8080
  /auth/*, /me*        → user-service :8081
  /products*           → product-service :8082
  /media/*             → media-service :8083
  /cart*, /orders*, /wishlist*  → order-service :8084   (NEW)
```

### 1.1 Why per-seller Orders, not one multi-seller Order

A cart can contain products from several sellers. At checkout, split cart items **by `sellerId`** and create **one `Order` document per seller**, tagged with a shared `checkoutGroupId` so the buyer's order-history view can visually group them. This is a deliberate choice, not an accident:

- It reuses the existing single-owner-document + ownership-check pattern already used for `Product` (`sellerId` field, `403`/`404` masking) instead of inventing per-line-item authorization inside one shared document.
- A seller's "manage my orders" screen becomes `GET /orders/mine?role=seller`, exactly parallel to the existing `GET /products/my` — no need to filter sub-arrays of someone else's document.
- Cancelling/updating status per seller doesn't risk one seller mutating another seller's slice of a shared object.

### 1.2 Service wiring changes

- `docker-compose.yml`: add an `order-service` block (mirrors `product-service`'s block) with `MONGODB_URI=mongodb://mongo:27017/orderservice`, `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`, JWT vars, Eureka vars, and `PRODUCT_SERVICE_BASE_URL=http://product-service:8082`. Add it to `api-gateway`'s `depends_on`.
- `api-gateway/src/main/resources/application.yml`: add a route:
  ```yaml
  - id: order-service-route
    uri: lb://order-service
    predicates:
      - Path=/cart,/cart/**,/orders,/orders/**,/wishlist,/wishlist/**
  ```
- `Jenkinsfile`: add `order-service` to the backend build loop.
- `docker-compose.prod.yml`, `docker-compose.ci.yml`, `docker-compose.sonar.yml`, `frontend/nginx*.conf`: extend wherever the other four services are listed by name (grep for `product-service` in each file to find every place that needs the twin entry).
- `sonar-project.properties`/CI workflow: add `order-service` to whatever loop or matrix runs backend Sonar analysis per module.

**Done when**: `docker compose up --build -d --wait` brings up 9 healthy containers, Eureka shows `ORDER-SERVICE` registered, and `curl http://localhost:8080/orders` returns `401` (route wired, security active) rather than `404` (route missing).

## 2. Database design

This is the section the first audit question checks directly — new collections, fields, and relationships, justified.

### 2.1 `orderservice.carts`

One cart per user, upserted, not versioned per-item history (it's working state, not a record).

| Field | Type | Notes |
|---|---|---|
| `id` | ObjectId | |
| `userId` | String, unique index | JWT `sub`; one cart per user |
| `items[]` | array | see below |
| `updatedAt` | Instant | |

`items[]` element:

| Field | Type | Notes |
|---|---|---|
| `productId` | String | → `productservice.products._id` |
| `sellerId` | String | denormalized at add-time so checkout grouping doesn't need a product lookup per item |
| `quantity` | Integer, ≥ 1 | |
| `unitPriceSnapshot` | BigDecimal | price *when added*, shown to the user as "price may have changed" if it drifts — re-validated authoritatively at checkout |
| `addedAt` | Instant | |

Relationship added: `carts.items[].productId → products._id` (application-level reference, same style as `products.sellerId → users` today — no Mongo joins, validated by calling `product-service`).

### 2.2 `orderservice.orders`

| Field | Type | Notes |
|---|---|---|
| `id` | ObjectId | |
| `checkoutGroupId` | String, indexed | groups sibling per-seller orders created from one checkout |
| `buyerId` | String, indexed | JWT `sub` of purchaser |
| `sellerId` | String, indexed | JWT `sub` of the single seller this order belongs to |
| `items[]` | array | `productId, name, unitPrice, quantity, imageId` — **snapshotted at order time**, so a later product edit/delete never mutates order history |
| `subtotal` | BigDecimal | derived, stored for query/display without recomputation |
| `status` | enum | `PENDING → CONFIRMED → SHIPPED → DELIVERED`, or `CANCELLED` from any pre-`DELIVERED` state |
| `paymentMethod` | enum | `CASH_ON_DELIVERY` (required scope); bonus: `CARD`, `PAYPAL` |
| `paymentStatus` | enum | `UNPAID → PAID`; cash-on-delivery flips to `PAID` on `DELIVERED` |
| `statusHistory[]` | array | `{status, changedAt, changedBy}` — the audit trail that answers "follow the order status" |
| `shippingAddress` | embedded | `line1, city, postalCode, country` — minimal, not a separate collection (1:1, never queried independently) |
| `version` | Long, `@Version` | optimistic locking, same as `Product`/`User` |
| `createdAt`, `updatedAt` | Instant | |

Indexes: `buyerId`, `sellerId`, `checkoutGroupId`, compound `(sellerId, status)` for the seller order-queue view, and a text index on `items.name` if keyword search over past orders is wanted (nice-to-have, not required by the brief).

Relationships added: `orders.buyerId → users`, `orders.sellerId → users`, `orders.items[].productId → products`. All application-level, validated via JWT claims and a synchronous `product-service` call at checkout — consistent with how `products.sellerId` and `media_assets.sellerId` are already validated against `user-service`/JWT rather than a DB-level foreign key.

### 2.3 `orderservice.wishlists` (bonus)

| Field | Type | Notes |
|---|---|---|
| `id` | ObjectId | |
| `userId` | String, unique index | one wishlist per user |
| `productIds[]` | array of String | no quantity/price — it's a save-for-later list, not a cart |
| `updatedAt` | Instant | |

### 2.4 No schema changes needed in `user-service` or `product-service`

Profile "best products / most-bought / money spent" and seller "best-selling / money gained" are **computed by aggregating `orderservice.orders`**, not stored as denormalized counters on `User`. Storing running totals on `User` would create a second source of truth that drifts the moment an order is cancelled or refunded; `order-service` already owns the ledger, so it computes and serves the aggregation (see §3.4). This mirrors the existing principle stated in the README: "each service owns its data."

`Product` gains no new field either — "best-selling products" is `order-service` grouping `orders.items` by `productId` and calling `product-service` for display names, not a `soldCount` counter on `Product` that two services would need to keep in sync.

**Done when**: the three new collections exist with the indexes above, `order-service` boots with `spring.data.mongodb.auto-index-creation: true` (matching the other services), and a migration note is added to this plan's history if any renaming happens later (the same way `README.md` §"Database design" documents the diagram-vs-implementation field mapping for buy-01 — do the same for any buy-02 renames so the audit table stays accurate).

## 3. API design

All new endpoints sit behind the Gateway JWT filter exactly like existing ones; role/ownership checks happen in `order-service` itself (defense in depth, same as Product/Media today).

### 3.1 Cart

| Method | Path | Access | Behavior |
|---|---|---|---|
| `GET` | `/cart` | Authenticated | Return the caller's cart (empty cart auto-created on first read) |
| `POST` | `/cart/items` | Authenticated | `{productId, quantity}` — validates product exists/in stock via `product-service`, upserts the line |
| `PUT` | `/cart/items/{productId}` | Authenticated, owner | `{quantity}` — `0` removes the line |
| `DELETE` | `/cart/items/{productId}` | Authenticated, owner | Remove one line |
| `DELETE` | `/cart` | Authenticated, owner | Clear cart |

Persistence across refresh (an explicit audit check) comes for free from server-side storage: the frontend cart service reads `GET /cart` on load instead of relying on `localStorage`.

### 3.2 Checkout / Orders

| Method | Path | Access | Behavior |
|---|---|---|---|
| `POST` | `/orders/checkout` | Authenticated | Body: `{shippingAddress, paymentMethod}`. Reads the caller's cart, groups by `sellerId`, calls `product-service` to atomically re-validate price/stock and **decrement stock**, creates one `Order` per seller sharing a `checkoutGroupId`, clears the cart, publishes `order.created` per order. `409` if any line's stock became insufficient between cart-add and checkout (message names the product) |
| `GET` | `/orders/mine` | Authenticated (buyer) | List the caller's orders; supports `?status=&q=&page=&size=` |
| `GET` | `/orders/selling` | SELLER | List orders where caller is `sellerId`; same query params — this is the "list orders for sellers using search" requirement |
| `GET` | `/orders/{id}` | Buyer or owning seller | `404` (not `403`) for anyone else — existence-masked, same convention as Product |
| `PATCH` | `/orders/{id}/status` | SELLER + owner | `{status}` — enforces legal transitions only (`PENDING→CONFIRMED→SHIPPED→DELIVERED`); rejects skipping states, returns `400` with the invalid-transition explained |
| `POST` | `/orders/{id}/cancel` | Buyer or owning seller, pre-`DELIVERED` only | Sets `CANCELLED`, restores stock via `product-service`, publishes `order.cancelled` |

"Redo" from the brief ("remove, cancel, or redo orders") is implemented as: cancel the existing order, then `POST /orders/{id}/reorder` which re-adds its items to the caller's current cart at **current** prices/stock (not the old snapshot) so the user goes through checkout again — this avoids silently re-charging a changed price.

### 3.3 Search and filtering (Product Service)

Extend the existing `GET /products` (no breaking change — new params are optional):

| Param | Meaning |
|---|---|
| `q` | keyword match against `name`/`description` (Mongo text index, added to `Product`) |
| `minPrice`, `maxPrice` | inclusive bounds |
| `sellerId` | scope to one seller (also useful for a public seller storefront view) |
| `inStock` | `true` → `stock > 0` |
| `sort` | `price_asc \| price_desc \| newest` |
| `page`, `size` | pagination — currently `GET /products` returns everything unpaginated; keep that as the no-param default for backward compatibility, but cap/paginate once `q`/filters are present to avoid surprising existing callers |

`GET /orders/mine` and `GET /orders/selling` reuse the same `q` convention against snapshotted item names/status, satisfying "list the orders ... using a search functionality."

### 3.4 Profile aggregation (served by `order-service`, consumed by the frontend profile pages)

| Method | Path | Access | Returns |
|---|---|---|---|
| `GET` | `/orders/stats/me` | Authenticated | `{topProducts[], mostBoughtProducts[], totalSpent}` computed from the caller's non-cancelled orders |
| `GET` | `/orders/stats/selling` | SELLER | `{bestSellingProducts[], totalRevenue}` computed from orders where caller is `sellerId` |

Both are Mongo aggregation pipelines (`$match → $unwind items → $group`) over `orders`; no new collection, no cross-service write.

### 3.5 Wishlist (bonus)

| Method | Path | Access |
|---|---|---|
| `GET` | `/wishlist` | Authenticated |
| `POST` | `/wishlist/items` | Authenticated — `{productId}` |
| `DELETE` | `/wishlist/items/{productId}` | Authenticated, owner |

**Done when**: every endpoint above returns the same JSON error shape as existing services (`timestamp, status, error, message, correlationId, details?`), and a Postman/HTTP-file collection or integration test exercises the full checkout→status-update→cancel lifecycle end to end.

## 4. Service-to-service integration

Following the existing precedent (product-service ↔ media-service is a **direct container call**, not routed through the Gateway):

- **Stock reservation/decrement** (`order-service → product-service`, synchronous, must succeed-or-fail with the order creation): new internal endpoint `PATCH /products/{id}/stock` on `product-service`, called via `PRODUCT_SERVICE_BASE_URL` — not exposed on the Gateway's public route table, so it's unreachable from the browser, mirroring how Media's ownership-metadata endpoint is used internally today. Input `{delta}` (negative to decrement at checkout, positive to restore on cancel); rejects if it would drive `stock` below `0`.
- **Eventual-consistency side effects** (`order-service` → Kafka), for anything that doesn't need to block the response:

| Topic | Producer | Consumer | Effect |
|---|---|---|---|
| `order.created` | order-service | *(none required; available for future audit/notification consumers)* | |
| `order.status.changed` | order-service | *(none required)* | |
| `order.cancelled` | order-service | *(none required — stock restore is synchronous, see above)* | |

This keeps the new Kafka surface symmetrical with the existing table in the README (some topics are produced with no mandatory consumer yet, which is already the accepted pattern for `product.created`/`product.updated`/`image.uploaded`).

**Done when**: a checkout that races a concurrent stock-out returns `409` instead of overselling, and killing `product-service` mid-checkout causes `order-service` to fail the checkout with `503` rather than silently creating an order for stock that was never reserved (mirrors the existing "Media ownership validation unavailable → 503" convention).

## 5. Frontend (Angular)

New lazy-loaded feature modules, matching the existing `catalog`/`seller`/`profile` module shape:

- **`cart/`** — cart page (line items, quantity steppers, remove, subtotal), a `CartService` backed by `GET/POST/PUT/DELETE /cart*`, a header cart-badge (extend `layout/header`) showing item count.
- **`checkout/`** — address form + "Cash on delivery" payment-method selector (bonus: radio group extended with mocked card/PayPal), calls `POST /orders/checkout`, shows the `409` stock-conflict error inline per affected line rather than a generic toast.
- **`orders/`** (buyer) — order history list with status filter/search, order detail with status timeline (`statusHistory`), cancel/reorder actions.
- **`seller/orders/`** — extends the existing `seller` module: incoming-orders queue, per-order status update control restricted to legal next-states, search.
- **`profile/`** — extend the existing `profile` page with a stats panel calling `/orders/stats/me`; add a seller-only stats panel (`/orders/stats/selling`) gated by the existing `RoleGuard`.
- **`catalog/`** — extend `product-list` with a search box + filter sidebar (price range, in-stock toggle, sort dropdown), calling the extended `GET /products` query params; debounce keyword input.
- **`wishlist/`** (bonus) — heart/save icon on `product-card`, a wishlist page.

Reuse, don't rebuild: `AuthGuard`, `RoleGuard`, the auth interceptor, and the Reactive Forms + validation-message pattern already established in `auth`/`seller`. New HTTP services follow the exact shape of `shared/services/product.ts`.

**Done when**: adding an item to the cart and reloading the page (hard refresh, not just Angular navigation) shows the same items and quantities — the literal audit check — because the cart is fetched from `GET /cart` on app init/login rather than held only in memory.

## 6. Security, error handling, validation

Apply the same measures already in place for buy-01, extended to the new surface:

- JWT validation at the Gateway + downstream, same secret/issuer/audience.
- Ownership enforcement: cart/order mutations check `buyerId`/`sellerId == JWT sub`; cross-user access is `404`, not `403`, matching the existing masking convention.
- Input validation: `quantity ≥ 1`, `price` fields never trusted from the client at checkout (server re-prices from `product-service`), status transitions whitelisted server-side.
- Global exception handler in `order-service` producing the shared error DTO shape (copy `product-service`'s `exception/` package as the template).
- Angular: inline field errors on checkout form, snackbar/toast for checkout failures (stock conflict, network/5xx), same pattern as existing upload-failure handling in `seller/product-media`.
- No new secrets or CORS surface — reuses `CORS_ALLOWED_ORIGINS`, `JWT_SECRET` already wired through Compose.

**Done when**: a CLIENT hitting a SELLER-only order-status endpoint gets `403`; a buyer requesting another buyer's order gets `404`; a checkout with stale/insufficient stock gets `409` with the offending product named.

## 7. CI/CD and SonarQube

- Add `order-service` to the Jenkinsfile's backend build loop and to whatever SonarQube scan step/matrix covers `product-service`/`user-service`/`media-service` today (check `.github/workflows/sonarqube.yml` and `scripts/run-sonar-backend.sh` for the loop to extend).
- Keep the PR-gated flow already documented in `SONAR_QUICKSTART.md`: every buy-02 feature branch goes through a PR into `main`, the `build-and-analyze` required check must pass, one approval required.
- As SonarQube flags issues in the new module, fix them in the same PR (or a fast-follow PR) and note the fix the way `SONAR_QUICKSTART.md` §14 already documents a resolved duplicated-literal issue — the audit explicitly asks for *documented* SonarQube-driven improvements, so keep a short running note (in the PR description or a `CHANGES.txt` like `media-service` already has) rather than only fixing silently.

**Done when**: a PR touching `order-service` triggers the same `build-and-analyze` check as the existing services, and it's `required` in branch protection for `main`.

## 8. Collaborative process (audited directly)

The brief scores this independently of the code, so make it visible in the repo, not just true in practice:

- One feature branch + one PR per bullet in §9 below — not one giant PR for all of buy-02. Small PRs are what makes "code reviews for each PR" checkable.
- Every PR description states what changed and why, links back to the relevant instruction bullet, and — once merged — is left as the record of review (approval + passing CI) the audit will read.
- Every PR gets at least one review comment thread resolved before merge, even on a solo project — self-review via a second pass, or ask a peer, to leave an actual reviewed trail rather than an instant self-approve.
- Keep `main` always deployable: merge only after CI is green, never push directly to `main` (branch protection already requires this per `SONAR_QUICKSTART.md` §7).

**Done when**: the repository's closed-PR list shows ~10–15 scoped PRs (not 1–2 huge ones), each with CI status and review visible.

## 9. Suggested PR sequence

1. `order-service` skeleton: module scaffold, health check, Eureka registration, Gateway route, Compose wiring — no business logic yet, just "the ninth service boots healthy."
2. `Cart` model + endpoints + unit tests.
3. `Order` model + `POST /orders/checkout` (incl. synchronous stock decrement endpoint on `product-service`) + status/cancel endpoints + unit tests.
4. Frontend cart module + header badge.
5. Frontend checkout + order-history (buyer) modules.
6. Frontend seller order-management module.
7. Product search/filter (backend query params + frontend search/filter UI).
8. Profile/seller stats aggregation endpoints + frontend stats panels.
9. Wishlist (bonus), end to end.
10. Extra payment methods (bonus), end to end.
11. SonarQube cleanup pass(es) as issues surface from the above.
12. Docs pass: update `README.md`'s architecture diagram, routing table, DB table, and Kafka table to include `order-service`; restore/author `PLAN.md` with the buy-02 manual runtime scenarios (cart-survives-refresh, multi-seller checkout, status transitions, ownership masking, search/filter, stats accuracy) the same way it currently promises to cover buy-01's scenarios.

## 10. Testing strategy

- **Unit tests** in `order-service` (JUnit, mirroring `product-service/src/test`): cart upsert logic, checkout grouping-by-seller, status-transition whitelist, stock-conflict handling, stats aggregation pipeline logic.
- **Frontend unit tests**: `CartService`, `OrderService`, and the new guards/intercePtor usage, following the existing `*.spec.ts` pattern next to each service/guard.
- **Integration/manual scenarios** (record in `PLAN.md`, see PR 12 above): register two sellers + one buyer → buyer adds products from both sellers to cart → checkout → two `Order`s share one `checkoutGroupId` → each seller only sees/updates their own order → buyer cancels one → stock restored → refresh browser mid-cart-build confirms persistence → keyword+price-range search returns expected subset → stats panel total matches sum of non-cancelled order subtotals.
- **No unhandled 5xx**: exercise the same negative-path matrix already used for buy-01 (invalid input, wrong role, cross-owner, expired/tampered JWT) against every new endpoint.

## 11. Bonus features

- **Wishlist**: scoped in §2.3/§3.5/§5 above — smallest bonus, do it first if time is short.
- **Additional payment methods**: keep `paymentMethod` an enum (`CASH_ON_DELIVERY` required; add `CARD`, `PAYPAL`) with **no real payment gateway integration** — mock the confirmation step (`paymentStatus` flips based on method: cash stays `UNPAID` until delivery, card/PayPal flip to `PAID` immediately as a simulated instant-capture). Explicitly scope this as a UI/flow bonus, not a PCI-scope payment integration, to avoid taking on real cardholder-data handling in a school project.

## 12. Audit question → plan section map

| Audit question (paraphrased) | Answered by |
|---|---|
| Tables/fields/relations added; DB design correct; new relationships used correctly | §2 (schema), §2.4 (why no denormalized counters), §4 (relationship validation) |
| PRs and code reviews happening; collaborative process | §8, §9 |
| Orders/Profile/Search/Cart functionality consistent with instructions, clean, no errors | §3, §5, §6, §10 |
| Cart survives refresh with correct quantities | §2.1, §3.1, §5 "Done when" |
| SonarQube issues addressed and documented | §7 |
| UI responsive/user-friendly | §5 (reuses existing responsive shell — verify explicitly per new page, no new framework needed) |
| Error handling/validation graceful | §6 |
| Security measures consistently applied | §6 |
| CI/CD (Jenkins) correctly set up and used for PRs | §7, existing `Jenkinsfile` |
| Branches merged correctly, `main` up to date | §8 |
| Full functional test passes | §10 |
| Unit tests present for critical parts | §10 |
| Wishlist works (bonus) | §11 |
| Payment methods work (bonus) | §11 |

## 13. Open decisions to confirm before starting

- **Cart persistence model**: this plan assumes server-side (`order-service`-backed) cart rather than `localStorage`-only, because it's the more convincing answer to "add products, refresh, are they still there" across devices/sessions and because checkout needs a server-side source of truth anyway. Confirm this is acceptable scope before building — a `localStorage`-only cart is faster to build but weaker evidence for the audit.
- **Multi-seller order splitting**: this plan splits one checkout into one `Order` per seller (§1.1). If graders expect a single Order object with mixed sellers, this is a one-collection reshape, not a rewrite — flag now if that's a hard requirement rather than after §3 is built.
- **`PLAN.md`**: currently referenced by `README.md` but absent from the repo. Restoring it (PR 12) is included here because the audit questions read like a runtime checklist the way `PLAN.md` is described to be used — confirm whether that's expected to exist for buy-02 or whether this plan document supersedes it.
