# Phase 6 Completion and Quality Report

## Scope

Phase 6 verifies the required buy-02 functionality, responsive behavior, validation, security, automated tests, and SonarQube Quality Gates. Bonus wishlist and additional payment methods are outside this required completion report.

## Functional verification

The acceptance scenarios were run manually against the rebuilt Docker Compose stack and passed:

- Persistent cart contents and quantities across browser refresh.
- Cash-on-delivery checkout and per-seller order creation.
- Buyer and seller order listing, search, status filtering, cancellation, reorder, and stock restoration.
- Product keyword, price, seller, stock, sorting, and pagination filters.
- Responsive cart, order, catalog, search/filter, and profile interfaces on mobile and desktop layouts.
- Buyer spending/top-product statistics and seller revenue/best-selling-product statistics.
- Cancelled orders excluded from statistics and seller data isolated by authenticated seller identity.
- Empty statistics states for new buyer and seller accounts.
- Authentication/authorization negative paths, including `401` without authentication and `403` for buyer access to seller statistics.
- Expired JWT handling: Angular removed the local session and the Gateway/backend rejected a genuinely expired signed token with `401 Unauthorized`.

The catalog's technical Seller ID input was replaced with a seller-name dropdown. A public read-only `GET /sellers` endpoint supplies only `{id, username}`; email and other private profile data are not exposed.

## Automated verification

The affected backend suites, Angular unit tests, and Angular production build passed locally. Focused tests were added for the seller directory and for the SonarQube coverage gaps described below.

## SonarQube findings and corrections

The initial Phase 6 analysis passed reliability, security, maintainability, and duplication conditions but failed the 80% new-code coverage condition in two projects:

| Project | Initial new-code coverage | Required |
|---|---:|---:|
| order-service | 75.0% | 80.0% |
| product-service | 55.1% | 80.0% |

The correction added behavioral tests for:

- Successful checkout and per-seller order splitting.
- Empty-cart rejection.
- Stock restoration when checkout fails after an earlier reservation.
- Product search/filter validation and supported sorting modes.
- HTTP controller forwarding of product search parameters.

No production behavior was weakened and no Quality Gate threshold was changed.

## Final SonarQube result

The final `build-and-analyze` rerun passed the Quality Gate for all seven analysed projects:

1. API Gateway
2. Discovery Service
3. Media Service
4. Order Service
5. Product Service
6. User Service
7. Angular frontend

One intermediate scanner run failed while constructing SonarQube active rules because the local WSL runner lost its SSL connection and Docker/SonarQube became unresponsive. Restarting the local Docker/WSL environment and rerunning the unchanged job resolved the infrastructure failure. It was not a source-code or Quality Gate defect.

## Documentation update

The README now reflects the implemented Buy-02 architecture, API and web routes, MongoDB collections and application-level relationships, Kafka topics, security/error conventions, local runtime procedure, test commands, CI/CD workflow, and optional-feature status.

## Final audit checklist

| Audit area | Result | Evidence |
|---|---|---|
| Database tables/collections, fields, and relationships | Pass | Service-owned `users`, `products`, `media_assets`, `carts`, and `orders`; unique cart/user index, order query indexes, embedded snapshots, ownership IDs, and optimistic locking are documented in the README. |
| Orders microservice | Pass | Buyer/seller lists and search, details, status management, cancellation, reorder, checkout, stock reservation/restoration, and statistics passed the acceptance scenarios. |
| Shopping cart persistence | Pass | Products and selected quantities remained after refresh; add/update/remove/clear and the live badge were verified. |
| Search and filtering | Pass | Keyword, price, seller-name dropdown, stock, sorting, pagination, and order filters were verified. |
| User and seller profiles | Pass | Buyer spending/top-product and seller revenue/best-selling-product results matched non-cancelled order history. |
| Responsive and user-friendly UI | Pass | Catalogue, filter, cart, checkout, order, and profile views were checked on desktop and mobile layouts. |
| Error handling and validation | Pass | Empty/invalid input, insufficient stock, dependency failures, and API error presentation use controlled responses/messages. |
| Security | Pass | JWT expiry, unauthenticated `401`, wrong-role `403`, ownership isolation, BCrypt storage, CORS configuration, and secret injection were checked. |
| Unit/behavioural tests | Pass | Backend and Angular suites cover critical authentication, ownership, cart/order, checkout rollback, filtering, and UI behaviour; the production frontend build passed. |
| SonarQube | Pass | All seven projects passed the final Quality Gate; coverage findings and the added tests are recorded above. |
| Jenkins CI/CD | Pass, release confirmation pending | The multibranch pipeline builds/tests, publishes reports, archives artifacts, supports gated staging deployment/rollback testing, and sends notifications. Confirm the final PR run is green before merge. |
| PR collaboration and reviews | Release confirmation pending | Feature work used PRs and branch protection. The final documentation PR must still be marked ready, approved by a teammate, and merged with required checks green. |
| Wishlist bonus | Not implemented | Optional and outside the required scope. |
| Additional payment-method bonus | Not implemented | Optional; required cash on delivery is implemented and verified. |

## Remaining release actions

- Move the draft PR to ready for review.
- Obtain teammate approval, confirm Jenkins and GitHub Actions remain green, and merge into `main`.
- Synchronize the merged GitHub `main` branch to Gitea.
