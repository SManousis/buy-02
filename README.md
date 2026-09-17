# Buy-02 E-Commerce Platform

Buy-02 is a responsive e-commerce application built with Spring Boot microservices, Angular, MongoDB, Kafka, Eureka, and an API Gateway. It completes the required shopping-cart, cash-on-delivery checkout, order-management, product-search/filtering, and buyer/seller profile-statistics features.

## Implemented features

- JWT registration/login with buyer and seller roles.
- Product catalogue, product details, seller product management, and media uploads.
- Keyword, price, seller, availability, sorting, and pagination filters.
- Persistent server-side cart with quantity controls and an immediately updated header badge.
- Cash-on-delivery checkout, with mixed carts split into one order per seller.
- Buyer and seller order lists, search/status filters, order details, cancellation, reorder, and seller status updates.
- Buyer spending/most-purchased-product statistics and seller revenue/best-selling-product statistics.
- Responsive Angular pages and consistent validation/error feedback.

The optional wishlist and additional payment methods are not implemented. Checkout currently supports `CASH_ON_DELIVERY`, as required by the project brief.

## Architecture and ports

```text
Browser :4200
    |
API Gateway :8080 ---- Discovery/Eureka :8761
    |
    +-- User Service    :8081 -- userservice.users
    +-- Product Service :8082 -- productservice.products
    +-- Media Service   :8083 -- mediaservice.media_assets
    +-- Order Service   :8084 -- orderservice.carts / orderservice.orders
                                      |
                              Kafka :9092

MongoDB host port: 27018 (container port 27017)
```

Only the Gateway is used by the browser for backend requests. Service-to-service discovery uses Eureka in the Compose environment.

| Component | Host URL | Purpose |
|---|---|---|
| Angular frontend | `http://localhost:4200` | Web UI |
| API Gateway | `http://localhost:8080` | Public API and JWT enforcement |
| Eureka | `http://localhost:8761` | Service registry |
| MongoDB | `mongodb://localhost:27018` | Development database |
| Kafka | `localhost:9092` | Domain-event broker |
| SonarQube | `http://localhost:9000` | Local code-quality server (separate Compose file) |

## Prerequisites

- Docker Desktop with Docker Compose.
- For running outside Docker: Java 21, Maven, Node.js 22, and npm.
- A non-empty `JWT_SECRET` of at least 32 characters.

Do not commit `.env`, tokens, passwords, or SonarQube credentials.

## Configuration

Create the local environment file from the tracked template:

```bash
cp .env.example .env
```

On PowerShell:

```powershell
Copy-Item .env.example .env
$jwtSecret = [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")
$jwtSecret
```

Put the generated value after `JWT_SECRET=` in `.env`. All services must use the same `JWT_SECRET`, `JWT_ISSUER`, and `JWT_AUDIENCE`. The current default audience remains `buy-01-api` for compatibility with the inherited Buy-01 configuration; the value may be renamed only if it is changed consistently in every service and environment.

Important variables are documented in `.env.example`. Production overrides live in `docker-compose.prod.yml`; CI and SonarQube use their corresponding Compose files.

## Run the application

```bash
docker compose up --build -d
docker compose ps
```

Wait until the services are healthy, then open `http://localhost:4200`. Useful checks:

```bash
curl http://localhost:8761/actuator/health
curl http://localhost:8080/actuator/health
```

The four business-service ports are internal to the Compose network and are intentionally not published on the host. Use `docker compose ps` for their health state and Eureka to confirm registration.

Protected Gateway endpoints should return `401` without a valid bearer token. Stop the stack with:

```bash
docker compose down
```

If port `4200`, `8080`, `8761`, `9000`, `9092`, or `27018` is already allocated, stop the conflicting process/container or change the matching host-side port in the relevant Compose file.

## Web routes

| Route | Access | Purpose |
|---|---|---|
| `/` and `/products` | Public | Catalogue and filters |
| `/products/:id` | Public | Product details and add to cart |
| `/auth/login`, `/auth/register` | Public | Authentication |
| `/cart` | Authenticated | Persistent cart |
| `/cart/checkout` | Buyer | Shipping details and cash-on-delivery checkout |
| `/orders`, `/orders/:id` | Buyer | Order history, search, filters, details, cancel/reorder |
| `/profile` | Authenticated | Account and buyer/seller statistics |
| `/seller` | Seller | Seller dashboard |
| `/seller/products/new` | Seller | Create product |
| `/seller/products/edit/:id` | Seller | Edit product |
| `/seller/products/:id/media` | Seller | Product media |
| `/seller/orders`, `/seller/orders/:id` | Seller | Seller order management |

## API overview

The Gateway exposes these route families:

| API | Service | Authentication |
|---|---|---|
| `/auth/**`, `/me/**`, `/sellers` | user-service | Auth endpoints and seller directory are public; profile is protected |
| `/products/**` | product-service | Reads are public; writes require seller ownership |
| `/media/**` | media-service | Product media rules apply |
| `/cart/**`, `/orders/**` | order-service | JWT required |

### Authentication and profiles

- `POST /auth/register`
- `POST /auth/login`
- `GET /me`
- `PUT /me`
- `GET /sellers` — returns only seller `id` and `username` for the catalogue dropdown.

### Products and filtering

- `GET /products/{id}`
- `GET /products?q=&minPrice=&maxPrice=&sellerId=&inStock=&sort=&page=&size=`
- `POST /products`
- `PUT /products/{id}`
- `DELETE /products/{id}`

Supported sort values are `newest`, `price_asc`, and `price_desc`. `PATCH /products/{id}/stock` is an internal endpoint used by order-service for atomic stock reservation/restoration.

### Cart

- `GET /cart`
- `POST /cart/items`
- `PUT /cart/items/{productId}`
- `DELETE /cart/items/{productId}`
- `DELETE /cart`

The cart is stored in MongoDB, so its products and selected quantities survive a browser refresh and a new login session.

### Checkout, orders, and statistics

- `POST /orders/checkout`
- `GET /orders/mine?status=&q=`
- `GET /orders/selling?status=&q=`
- `GET /orders/{id}`
- `PATCH /orders/{id}/status`
- `POST /orders/{id}/cancel`
- `POST /orders/{id}/reorder`
- `GET /orders/stats/me`
- `GET /orders/stats/selling`

The normal status path is `PENDING -> CONFIRMED -> SHIPPED -> DELIVERED`. Eligible orders may be cancelled before delivery. Access is derived from the authenticated JWT; clients cannot select another buyer or seller identity.

## Database design

Each service owns its MongoDB data. References across service boundaries are application-level IDs, not database joins.

| Database / collection | Important fields and relationships |
|---|---|
| `userservice.users` | Account identity, unique username/email, BCrypt password hash, and role |
| `productservice.products` | Product data, price, stock, and `sellerId -> users._id` |
| `mediaservice.media_assets` | Media metadata and `productId -> products._id` |
| `orderservice.carts` | Unique `userId -> users._id`; embedded items contain `productId`, `sellerId`, quantity, and price/name snapshots |
| `orderservice.orders` | `buyerId` and `sellerId -> users._id`; embedded product snapshots, totals, shipping address, payment/status history, timestamps, and optimistic-lock version |

One persistent cart belongs to one authenticated user. A checkout is grouped by `checkoutGroupId` and creates one order per seller, preventing one seller from viewing or managing another seller's lines. Product name and price snapshots preserve the historical order even if the catalogue changes later. Statistics are aggregated from non-cancelled orders instead of duplicating mutable totals in the user or product collections.

MongoDB indexes and validation annotations enforce the uniqueness and required-field constraints used by the services. Stock changes are performed atomically; checkout restores earlier reservations if a later reservation fails.

## Kafka events

Kafka decouples domain events from the synchronous request path.

| Topic | Producer |
|---|---|
| `product.created` | product-service |
| `product.updated` | product-service |
| `product.deleted` | product-service |
| `image.deleted` | media-service |
| `order.created` | order-service |
| `order.status.changed` | order-service |
| `order.cancelled` | order-service |

The stock reservation required for checkout remains synchronous so that the API can return `409 Conflict` instead of overselling.

## Validation, errors, and security

- BCrypt password hashing and short-lived signed JWT authentication.
- Role and ownership authorization in the Gateway and services.
- `401 Unauthorized` for missing/invalid/expired authentication and `403 Forbidden` for an authenticated user with the wrong role.
- Ownership masking where appropriate so another user's resource is not disclosed.
- DTO/Bean Validation for incoming data and global exception handlers for consistent JSON errors.
- Product/stock conflicts return `409`; unavailable dependencies return a controlled service error.
- CORS is restricted by configuration. Production uses the HTTPS/Nginx overlay and security headers.
- Secrets are supplied through environment variables or Jenkins/GitHub credentials, never source control.

CSRF is disabled for these stateless bearer-token APIs because authentication is sent explicitly in the `Authorization` header rather than an automatically attached session cookie.

## Tests and verification

Run backend tests from each Java module. Use its Maven wrapper when present, otherwise use the installed Maven command:

```bash
cd order-service
./mvnw test
# or: mvn test
```

Run the frontend verification from one operating-system environment. Do not share `frontend/node_modules` between Windows and WSL because esbuild installs a platform-specific binary.

```bash
cd frontend
npm ci
npm test -- --watch=false
npm run build
npm run lint
```

The manual acceptance pass verified persistent cart quantities, checkout and per-seller splitting, order search/status/cancel/reorder, stock restoration, all product filters, buyer/seller statistics, responsive layouts, validation, role enforcement, and expired-JWT handling. See [PHASE6_AUDIT_REPORT.md](PHASE6_AUDIT_REPORT.md) for the recorded results.

## Collaboration, CI/CD, and SonarQube

Development follows feature branches and pull requests into protected `main`. Each PR requires a teammate approval, resolved conversations, and the required `build-and-analyze` check before merge. After a GitHub merge, synchronize the resulting `main` commit to the Gitea remote.

- `Jenkinsfile` polls the repository, builds and tests all six Java applications and Angular, publishes JUnit results, archives artifacts, sends email notifications, and can deploy to staging when `DEPLOY_ENV=staging` and `SKIP_DEPLOY=false`.
- `.github/workflows/sonarqube.yml` runs on pushes and PRs to `main` using the WSL/Linux self-hosted runner, because the SonarQube server is local and is not exposed through ngrok.
- SonarQube analyses cover API Gateway, Discovery, Media, Order, Product, User, and the Angular frontend.

The final Phase 6 rerun passed all seven Quality Gates. Earlier new-code coverage failures in order-service and product-service were fixed with behavioural checkout/rollback and product-filter tests; no threshold was reduced. Setup and recovery steps are in [SONAR_QUICKSTART.md](SONAR_QUICKSTART.md).

## Project documentation

- [buy02Plan.md](buy02Plan.md) — phased implementation and audit checklist.
- [PHASE3_WORKFLOW.md](PHASE3_WORKFLOW.md) — cart/checkout/order workflow notes.
- [PHASE5_WORKFLOW.md](PHASE5_WORKFLOW.md) — search/filter and profile-statistics workflow.
- [PHASE6_AUDIT_REPORT.md](PHASE6_AUDIT_REPORT.md) — final functional and quality evidence.
- [instructions.md](instructions.md) — GitHub runner, Jenkins, and repository workflow instructions.
- [SONAR_QUICKSTART.md](SONAR_QUICKSTART.md) — local SonarQube and self-hosted-runner guide.

## Final release procedure

1. Commit changes on the feature/documentation branch and push to GitHub and Gitea.
2. Open or update the GitHub PR targeting `main`.
3. Confirm Jenkins and `build-and-analyze` pass and the seven SonarQube Quality Gates are green.
4. Obtain teammate approval and resolve review conversations.
5. Merge the PR, switch to `main`, pull the merged commit, and push that same `main` to Gitea.
