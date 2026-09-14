# Buy-01

Buy-01 is an e-commerce application built with Angular and Spring Boot microservices. It supports public product browsing, CLIENT and SELLER accounts, seller-owned product CRUD, seller avatars, and dedicated product-image management.

This document describes the configuration and contracts implemented in this repository. It is not evidence that the manual 01-edu runtime audit has passed; run the automated checks and manual role/media/HTTPS scenarios in [`PLAN.md`](PLAN.md) against a running stack before recording audit results.

For the reproducible Jenkins CI/CD installation, credentials, permissions, deployment, rollback, notifications, backup, and troubleshooting procedures, see [`JENKINS_SETUP.md`](JENKINS_SETUP.md).

## Architecture and public routing

```text
Browser
  | development Docker: http://localhost:4200
  | production:         https://SERVER_NAME
  v
Angular + Nginx
  | /api/* (Nginx removes /api)
  v
Spring Cloud Gateway :8080
  | /auth/*, /me*       -> user-service :8081
  | /products*          -> product-service :8082
  | /media/*            -> media-service :8083
  v
MongoDB + Kafka; Eureka provides service discovery
```

Nginx is the browser-facing entry point in Docker. The production Angular build calls same-origin `/api`; Nginx strips that prefix and proxies to the Gateway. The Gateway itself is also published at `http://localhost:8080` by the development Compose file, where routes have no `/api` prefix. User, Product, and Media ports are internal-only in Compose.

| Entry point | Development URL | Notes |
|---|---|---|
| Web application | `http://localhost:4200` | Dockerized Nginx, or Angular dev server |
| Gateway API | `http://localhost:8080` | Direct development/API-testing access |
| Eureka dashboard | `http://localhost:8761` | Registry and instance inspection |
| MongoDB | `mongodb://localhost:27018` | Host port mapped to container port 27017 |
| Kafka | `localhost:9092` | Published port; Compose advertises `kafka:9092` to container clients |

Local HTTP is development-only. The production overlay terminates TLS at Nginx and redirects port 80 to HTTPS.

## Prerequisites

For the complete containerized stack:

- Docker Engine or Docker Desktop with Docker Compose v2.24.4 or newer (required for production port-reset tags)
- Enough free space to build six application images (five Java services plus Angular/Nginx) and store MongoDB and media volumes

For source development and the complete verification script:

- Java 21
- Node.js 22 and npm 10 (matching the frontend build image/package metadata)
- A POSIX-compatible shell such as Bash, Git Bash, WSL, Linux, or macOS
- Docker Compose for configuration validation and container-backed development

Maven does not need to be installed globally; the repository Maven wrapper is used.

## Environment configuration

Copy the example before using Compose:

```bash
cp .env.example .env
```

Replace `JWT_SECRET` with an environment-specific random value of at least 32 characters. Keep `.env` untracked and use a secret manager in production. Every token-issuing or token-validating service must use the same secret, issuer, and audience.

### Compose `.env` variables

| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | Yes | HS256 signing/verification key; minimum 32 characters |
| `JWT_ISSUER` | No | JWT issuer, default `user-service` |
| `JWT_AUDIENCE` | No | JWT audience, default `buy-01-api` |
| `CORS_ALLOWED_ORIGINS` | No | Exact development browser origins; default `http://localhost:4200` |
| `SERVER_NAME` | Production only | DNS name used by Nginx and Let's Encrypt paths |

`CORS_ALLOWED_ORIGINS` accepts a comma-separated list because Spring binds it to a list. Do not include URL paths or a trailing slash. The production overlay sets each service's allowed origin to `https://${SERVER_NAME}`.

### Service-level variables

These defaults come from each `application.yml`. Compose supplies container-specific values for the full stack.

| Variable | Service(s) | Source-mode default / meaning |
|---|---|---|
| `SERVER_PORT` | discovery/gateway/user/product/media | `8761`, `8080`, `8081`, `8082`, `8083` respectively |
| `MONGODB_URI` | user/product/media | `mongodb://localhost:27018/userservice`, `productservice`, or `mediaservice` |
| `KAFKA_BOOTSTRAP_SERVERS` | product/media | `localhost:9092` |
| `JWT_SECRET` | gateway/user/product/media | Required, no application default, minimum 32 characters |
| `JWT_EXPIRATION_MS` | user/product/media | `86400000`; minimum accepted value is 60000 ms |
| `JWT_ISSUER` | gateway/user/product/media | `user-service` |
| `JWT_AUDIENCE` | gateway/user/product/media | `buy-01-api` |
| `CORS_ALLOWED_ORIGINS` | gateway/user/product/media | `http://localhost:4200`; comma-separated exact origins |
| `EUREKA_CLIENT_ENABLED` | gateway/user/product/media | `false`; Compose sets `true` |
| `EUREKA_DEFAULT_ZONE` | gateway/user/product/media | `http://localhost:8761/eureka/`; Compose uses the discovery container |
| `EUREKA_PREFER_IP_ADDRESS` | gateway/user/product/media | `true` |
| `MEDIA_SERVICE_NAME` | user | `media-service`, used for load-balanced avatar ownership calls |
| `MEDIA_SERVICE_BASE_URL` | product | `http://localhost:8083`, used for media ownership checks |
| `MEDIA_STORAGE_PATH` | media | `./media-storage`; Compose uses `/app/media-storage` |
| `MEDIA_PUBLIC_BASE_URL` | media | `http://localhost:8080/media/images`; Compose returns `/api/media/images` URLs |
| `GATEWAY_MEDIA_MAX_REQUEST_SIZE` | gateway | `3MB`, allowing multipart framing around a maximum 2 MiB image |

## Start the complete Docker stack

From the repository root:

```bash
docker compose config --quiet
docker compose up --build -d --wait
docker compose ps
```

`--wait` returns only after services with health checks report healthy, or exits non-zero on a startup/health failure. All eight services declare health checks, so `--wait` gates on the whole stack. Then open `http://localhost:4200`, inspect the registry at `http://localhost:8761`, and call the Gateway at `http://localhost:8080`.

`--wait` proves container health, not Gateway routing readiness. For a few seconds after a cold start, every container reports `healthy` while proxied calls still return `503 Service Unavailable`, because the Gateway resolves `lb://` route targets through Eureka and that registration lags its own actuator health. Do not follow `--wait` with a fixed `sleep`; poll a real public route until it answers:

```bash
until curl -fsS -o /dev/null http://localhost:4200/api/products; do sleep 1; done
```

Scripts, CI jobs, and smoke tests that skip this gate will see spurious `503`s that are not defects.

Follow logs when diagnosing startup:

```bash
docker compose logs -f discovery-service api-gateway user-service product-service media-service
```

Stop containers without deleting persistent data:

```bash
docker compose down
```

`docker compose down -v` also deletes the MongoDB and uploaded-media volumes. Use it only when an intentional clean reset is acceptable.

## Local frontend development

The least surprising hot-reload workflow keeps infrastructure and backend services in Docker and runs Angular from source:

```bash
docker compose up --build -d --wait mongo kafka discovery-service user-service product-service media-service api-gateway
cd frontend
npm ci
npm start
```

Open `http://localhost:4200`. The development Angular environment sends API requests directly to `http://localhost:8080`, and the Compose CORS default permits that origin. Do not also start the Compose `frontend` service, because it uses the same host port.

For backend source development, provide MongoDB, Kafka, and Eureka endpoints reachable from the host, export the service-level variables above, and start the required applications with Java 21. Enable Eureka for Gateway, User, Product, and Media: Gateway uses it for `lb://` routes, the domain services register there, and User uses it for avatar Media lookup. Product Service uses `MEDIA_SERVICE_BASE_URL` directly. Keep JWT settings identical across all four secured applications. The Compose Kafka broker advertises its container hostname, so a host-run backend needs a broker/listener that advertises a host-reachable address.

## Web pages

| Path | Access | Purpose |
|---|---|---|
| `/` | Public | Home/catalog highlights |
| `/products` | Public | Unfiltered product list |
| `/products/:id` | Public | Product detail and images |
| `/auth/register` | Public | CLIENT or SELLER registration |
| `/auth/login` | Public | Email/password sign-in |
| `/profile` | Authenticated | Profile and seller avatar management |
| `/seller` | SELLER | Own-product dashboard |
| `/seller/products/new` | SELLER | Product creation |
| `/seller/products/edit/:id` | SELLER | Product editing |
| `/seller/products/:id/media` | SELLER | Dedicated product-media management |

Frontend route guards improve navigation, but Gateway and downstream Spring Security rules remain the authorization boundary.

## API conventions

The paths below are Gateway paths. Use them as written with direct Gateway access, for example `http://localhost:8080/products`. When calling through Dockerized Nginx, prefix them with `/api`, for example `http://localhost:4200/api/products` or `https://${SERVER_NAME}/api/products`.

Send protected calls with:

```http
Authorization: Bearer <token>
```

JWT `sub` is the immutable user ID; the `role` claim is `CLIENT` or `SELLER`. Gateway and downstream services validate HS256 signature, expiry, issuer, and audience.

### Authentication and profile

| Method | Path | Access | Contract |
|---|---|---|---|
| `POST` | `/auth/register` | Public | `{ "username", "email", "password", "role": "CLIENT" | "SELLER" }` -> `201` |
| `POST` | `/auth/login` | Public | `{ "email", "password" }` -> `200`; login is by email, not username |
| `GET` | `/me` | Authenticated | Safe profile; password/hash is never returned |
| `PUT` | `/me` | Authenticated | `{ "username"?, "avatarMediaId"?, "removeAvatar"? }` |

Registration requires a 3-50 character username, a valid email, an 8+ character password, and a role. Email is lowercased and trimmed before uniqueness checks. Duplicate email or username returns `409`.

Register and login return:

```json
{
  "token": "<jwt>",
  "userId": "<user-id>",
  "username": "seller",
  "role": "SELLER"
}
```

`GET /me` and successful `PUT /me` return:

```json
{
  "id": "<user-id>",
  "username": "seller",
  "email": "seller@example.com",
  "role": "SELLER",
  "avatarMediaId": "<media-id-or-null>",
  "createdAt": "<timestamp>"
}
```

Only a SELLER may set `avatarMediaId`, and the ID must name an image owned by that seller. Upload the image first, then assign its ID with `PUT /me`. `removeAvatar: true` clears the profile reference but does not delete the Media asset. Sending both an avatar ID and `removeAvatar: true` is invalid. CLIENT avatar assignment is denied.

Render an avatar with `GET /media/images/{avatarMediaId}` (or `/api/media/images/{avatarMediaId}` through Nginx), not by treating the ID as a URL.

### Products

| Method | Path | Access | Result |
|---|---|---|---|
| `GET` | `/products` | Public | All products, unfiltered |
| `GET` | `/products/{id}` | Public | One product |
| `GET` | `/products/my` | SELLER | Authenticated seller's products |
| `POST` | `/products` | SELLER | Create own product, `201` |
| `PUT` | `/products/{id}` | SELLER + owner | Update own product |
| `DELETE` | `/products/{id}` | SELLER + owner | Delete own product, `204` |

Create body:

```json
{
  "name": "Olive oil",
  "description": "Cold pressed",
  "price": 12.50,
  "stock": 4,
  "imageIds": ["<media-id>"]
}
```

`name` (nonblank, at most 120 characters), `description` (nonblank, at most 2000 characters), and a price of at least `0.01` are required. `stock`, when present, must be zero or greater. `imageIds` may be absent or empty; every supplied ID must be nonblank, unique in the request, owned by the authenticated seller, and not associated with another product. Update uses the same fields as optional patches.

A Product response contains `id`, `sellerId`, `name`, `description`, `price`, `stock`, `imageIds`, `createdAt`, and `updatedAt`. `imageIds` is the canonical field. Legacy request/event readers and the frontend reader temporarily accept `imageUrls`, but responses and new events emit `imageIds` only.

Cross-seller Product mutation is existence-masked as `404`. A CLIENT cannot access seller endpoints and receives `403` with a valid token.

### Media

| Method | Path | Access | Result |
|---|---|---|---|
| `POST` | `/media/images` | SELLER | Multipart field `file`, `201` |
| `GET` | `/media/images/{id}` | Public | Image bytes with stored type, length, inline disposition, and one-hour revalidating cache policy |
| `GET` | `/media/images/{id}/metadata` | SELLER + owner | `{ id, sellerId, contentType, sizeBytes }` |
| `DELETE` | `/media/images/{id}` | SELLER + owner | Permanent deletion, `204` |

Accepted content is JPEG, PNG, or WEBP with a maximum file size of exactly 2 MiB (`2 * 1024 * 1024` bytes) and at most 16 million pixels. Validation checks declared MIME against signatures, parses/decodes the image, and rejects corrupt data and trailing payloads. Filename extensions alone are not trusted. Multipart requests have a separate 3 MB transport cap at Nginx/Gateway.

Upload returns the implemented Media DTO:

```json
{
  "id": "<media-id>",
  "sellerId": "<seller-id>",
  "originalFileName": "photo.png",
  "storedFileName": "<generated-name>.png",
  "contentType": "image/png",
  "size": 12345,
  "url": "/api/media/images/<media-id>",
  "createdAt": "<timestamp>"
}
```

Under Compose, `url` is browser-origin-relative and intended for the Nginx origin. A client talking directly to Gateway port 8080 must use `/media/images/{id}`, not the returned `/api/...` path. Direct source-mode Media defaults to an absolute `http://localhost:8080/media/images/{id}` URL. Product and profile documents store only Media IDs; construct public rendering URLs from the current API base rather than storing the upload URL in those documents.

Cross-seller Media metadata/deletion returns `403`. Media GET is deliberately public. Deleting an image is not the same as disassociating it from a Product and cannot be undone.

### Errors

JSON errors include a timestamp, HTTP status, reason, safe message, and correlation ID; validation errors can also include field details. Exact messages vary by service and failure type.

```json
{
  "timestamp": "2026-09-04T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "correlationId": "<request-id>",
  "details": { "email": "Email must be valid" }
}
```

Typical statuses are `400` validation/content/reference error, `401` missing or invalid JWT, `403` wrong role or Media ownership, `404` missing resource or masked Product ownership, `409` duplicate account, `413` request rejected by the Gateway transport cap, `503` Media ownership validation unavailable, and `500` unexpected server failure. Check the JSON `message` rather than assuming all services attach `details` in the same way.

## Health checks and Eureka

After startup:

```bash
docker compose ps
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8761/actuator/health
```

All eight Compose services declare a health check, so `docker compose up --wait` gates on every one of them:

| Service(s) | Probe declared in `docker-compose.yml` |
|---|---|
| Discovery, Gateway, User, Product, Media | `wget` against that container's own `/actuator/health` |
| `mongo` | `mongosh --eval "db.runCommand('ping').ok" --quiet` |
| `kafka` | `kafka-topics.sh --bootstrap-server localhost:9092 --list` |
| `frontend` | busybox `wget -S --spider` against `http://127.0.0.1:80/index.csr.html`, accepting `200` or `301` |

Each probe uses only binaries already present in that image. The `frontend` probe deliberately targets `127.0.0.1` rather than `localhost`, because both Nginx configurations use `listen 80;` (IPv4 only) while the container resolves `localhost` to `::1` first. It accepts a `301` as well as a `200` so that the same probe also passes under the production overlay, whose port-80 server block only redirects to HTTPS; it therefore confirms that Nginx is answering on port 80, not that the TLS listener is serving.

`docker compose ps` is the host-visible aggregate; the direct Gateway actuator endpoint reports Gateway health, not the downstream services' actuator payloads.

Container health is not the same as Gateway routing readiness. See Troubleshooting for the startup window in which every container is `healthy` but `/api/**` still returns `503`.

Open `http://localhost:8761` and verify current instances for `API-GATEWAY`, `USER-SERVICE`, `PRODUCT-SERVICE`, and `MEDIA-SERVICE`. Registration can lag application startup briefly. If a route fails, compare the registry with `docker compose ps` and service logs.

Actuator exposes `/actuator/health` and `/actuator/info` publicly on each Java application. User/Product/Media also expose `/actuator/metrics`; metrics require authentication and are not routed through the Gateway.

## Database design, storage, and persistence

MongoDB intentionally uses one database per domain service on the shared Mongo instance:

| Database / collection | Owned data | Cross-service references |
|---|---|---|
| `userservice.users` | Account, BCrypt password hash, role, profile | `avatarMediaId` -> Media ID |
| `productservice.products` | Seller-owned product and inventory fields | `sellerId` -> JWT user subject; `imageIds[]` -> Media IDs |
| `mediaservice.media_assets` | Media owner, MIME, size, storage key | `sellerId` -> JWT user subject |

These are application-level references, not MongoDB joins or foreign keys. Each service owns its data and validates identity/ownership through JWT claims and Media metadata calls. Public User DTOs never expose the password hash; Media owner metadata is seller/owner scoped.

The implemented field names differ from the official 01-edu database diagram on purpose. The mapping is intentional and complete:

| Official diagram field | Implemented field | Reason |
|---|---|---|
| product `userId` | `productservice.products.sellerId` | Names the role the reference plays; the value is the seller's JWT `sub` |
| product `quantity` | `productservice.products.stock` | Matches the inventory vocabulary used across the API and UI |
| media `imagePath` | `mediaservice.media_assets.storageKey` | The value is a service-generated storage key, never a client-supplied or client-visible filesystem path, and it is deliberately absent from every response DTO |
| product-to-image relation | `productservice.products.imageIds[]` | The canonical product-media relation; Media IDs, not URLs or paths |

These are the renames recorded against the diagram during the audit; compare the linked diagram in [`PLAN.md`](PLAN.md) directly if you need to confirm the rest of the schema field by field.

The `mongo-data` named volume persists all three databases. The `media-storage` volume persists image bytes separately from `mediaservice.media_assets`. Preserve and back up both volumes together. `docker compose down` keeps them; `docker compose down -v` removes them.

Product Service enforces exclusive non-empty `imageIds` with a unique partial multikey index. At startup, an idempotent migration renames legacy `imageUrls` only where `imageIds` does not already exist. Pre-existing duplicate/shared IDs can prevent migration or index creation and require manual reconciliation.

Exclusivity is enforced twice. A pre-save check rejects an image already attached to another product with `400` and a message naming the image. Behind it, the unique index catches the same conflict at save time when two concurrent writes both pass their pre-save check, and that `DuplicateKeyException` is translated into the same `400` reference error rather than surfacing as a `500`. The save-time branch is exercised by a unit test that injects the exception; it is unreachable from a sequential HTTP client, so it carries no runtime audit evidence.

## Kafka cleanup and consistency boundaries

| Topic | Producer | Consumer | Implemented effect |
|---|---|---|---|
| `product.created` | Product | None | Event available for future audit/indexing consumers |
| `product.updated` | Product | None | Event available for future audit/cache consumers |
| `product.deleted` | Product | Media | Delete each image referenced by the deleted Product |
| `image.uploaded` | Media | None | Event available for future processing consumers |
| `image.deleted` | Media | Product | Remove the deleted Media ID from referencing Products |

Cleanup is eventually consistent, not transactional:

- Database/storage mutations and Kafka publication are not atomic; there is no transactional outbox. Asynchronous publication can exhaust retries after a successful mutation.
- Product deletion cleanup treats already-missing Media as success and rethrows other failures so Kafka can retry.
- Media ownership validation and Product persistence are a validate-then-save sequence, not a distributed claim transaction. A concurrent deletion can race it.
- If `image.deleted` arrives after Product save, it repairs the stale reference. Delivery before the save, exhausted delivery, or lost publication requires event replay or an operator reconciliation of Product `imageIds` against Media metadata.
- Replacing a Product's `imageIds` only changes associations; it does not delete omitted Media. Use Media DELETE for permanent removal.
- Frontend upload/attach compensation is best effort. Closing the browser or process mid-flow can leave an unattached Media asset for operator cleanup.
- The avatar UI does not compensate by deleting a new upload when the subsequent profile request fails before persistence; that upload can also require operator cleanup.
- Assigning/removing/replacing `avatarMediaId` does not automatically delete the previous Media asset.

## Production HTTPS and certificate renewal

Prerequisites are a DNS A/AAAA record for `SERVER_NAME`, inbound ports 80 and 443, Certbot on the host, and permission for Docker to mount `/etc/letsencrypt` read-only. Obtain the first certificate while port 80 is free:

```bash
export SERVER_NAME=buy01.example.com
sudo certbot certonly --standalone -d "$SERVER_NAME"
docker compose -f docker-compose.yml -f docker-compose.prod.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d --wait
```

The overlay mounts `frontend/nginx.prod.conf`, resets all development host publications, publishes only frontend ports 80/443, redirects HTTP to HTTPS preserving path and query, enables TLS 1.2/1.3 only, and sends HSTS plus CSP, nosniff, frame, and referrer headers. Both server blocks set `server_tokens off;`, so neither the plaintext `301` nor any HTTPS response advertises an Nginx version. Certificate and key files remain on the host under `/etc/letsencrypt/live/${SERVER_NAME}`.

The configured standalone Certbot challenge needs port 80, and Nginx must reload renewed files. A maintenance-window renewal sequence is:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop frontend
sudo certbot renew --standalone
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d frontend
```

Test that renewal procedure with `sudo certbot renew --dry-run --standalone` while the frontend is stopped. Automate the same stop/renew/start sequence with host service hooks or a scheduler appropriate to the deployment. If renewal fails, restart the frontend immediately with the final command and inspect Certbot logs.

Verify the deployed endpoint rather than assuming TLS is correct:

```bash
curl -I "http://${SERVER_NAME}"
curl --fail --show-error -I "https://${SERVER_NAME}"
curl --fail --show-error "https://${SERVER_NAME}/api/actuator/health"
```

The first response should redirect to HTTPS; inspect the HTTPS response for `Strict-Transport-Security`. Do not expose Gateway or service HTTP ports publicly in production.

What has been verified, and what has not: the overlay's TLS behavior — redirect with path and query preserved, no version banner, the TLS 1.2/1.3 protocol floor, all five security headers on both the SPA and `/api` responses, `/api` reaching the Gateway over TLS, and the three commands above — was exercised against this configuration using a temporary, locally generated self-signed certificate on a throwaway host name. No certificate was ever requested from a real CA, and no DNS name or deployment host was involved. Certificate issuance, the Certbot renewal sequence above, and anything else that depends on a real CA or a real deployment host remain unverified and must be validated on the deployment host itself.

## Automated verification and manual audit

Run the complete repository automation from a POSIX-compatible shell:

```bash
bash scripts/verify.sh
```

The script runs all five backend Maven test suites, installs locked frontend dependencies, runs Angular tests and a production build, validates Compose with a development-only secret when none is supplied, and runs the production dependency audit. It exits non-zero on any failed step. It validates Compose configuration but does not start the stack or replace the manual 01-edu audit.

Its Compose validation covers the default file set only, which in this repository is `docker-compose.yml` alone. Neither the production overlay nor the TLS-specific merge is syntax-checked by the script, and the TLS merge additionally requires `SERVER_NAME` to interpolate. Validate them explicitly after editing `docker-compose.prod.yml` or `frontend/nginx.prod.conf`:

```bash
JWT_SECRET=local-validation-secret-at-least-32-chars SERVER_NAME=example.invalid \
  docker compose -f docker-compose.yml -f docker-compose.prod.yml config --quiet
```

Both variables are needed: the base file requires `JWT_SECRET` and the overlay requires `SERVER_NAME`, and each fails interpolation with a non-zero exit if it is unset. Neither value has to be real for a syntax check.

Use [`PLAN.md`](PLAN.md) for the runtime scenarios: CLIENT/Seller A/Seller B authorization, duplicate/invalid registration, public browsing, Product ownership, avatar ownership, the full JPEG/PNG/WEBP and 2 MiB boundary matrix, JWT tampering/expiry/issuer/audience, persistence and Kafka cleanup, response headers, and production HTTPS. Record results only from observed runs.

## Troubleshooting

- **Compose rejects interpolation or secured services do not start:** ensure `.env` exists and `JWT_SECRET` is at least 32 characters. Check `docker compose config` for the resolved structure without printing real secrets into tickets or logs.
- **Valid-looking tokens return 401:** issuer, audience, secret, or expiry differs between User, Gateway, Product, and Media. Tokens issued before a setting/secret change must be replaced.
- **Every container is `healthy` but `/api/**` returns 503 right after startup:** expected, and not a defect. Container health checks probe each service's own `/actuator/health`; the Gateway answers `UP` as soon as it boots, but its routes are `lb://` targets resolved through Eureka, so proxied calls fail until the downstream instance registration is visible to the Gateway's load balancer. This is a startup characteristic of the stack, and it also affects `docker compose restart`. Wait on a real route instead of on health alone: `until curl -fsS -o /dev/null http://localhost:4200/api/products; do sleep 1; done`. A run of the runtime audit that omitted this gate produced 57 spurious failures with no product defect behind any of them.
- **Gateway returns 5xx or routes are unavailable after the startup window:** inspect Eureka, `docker compose ps`, and `docker compose logs <service>`. Gateway routing depends on registered healthy instances.
- **Product/avatar update returns 503:** Media discovery/reachability failed. Product ownership checks use `MEDIA_SERVICE_BASE_URL`; User avatar checks use the Eureka `MEDIA_SERVICE_NAME`.
- **Browser reports CORS failure:** configure the exact scheme/host/port in `CORS_ALLOWED_ORIGINS`. An origin with the wrong port or a trailing slash does not match.
- **Upload returns 400:** inspect the JSON message for empty/oversize data, MIME/signature mismatch, corrupt/trailing content, unsupported format, or excessive dimensions. A transport-level request above 3 MB may be rejected earlier with 413.
- **Image metadata exists but bytes return 404:** Mongo metadata and `media-storage` are out of sync. Restore/reconcile both stores together; do not invent replacement storage keys.
- **Product migration/index startup failure:** inspect existing documents for `imageUrls`, conflicting `imageIds`, or a Media ID shared by products before retrying.
- **Port already allocated:** free or remap host ports 4200, 8080, 8761, 27018, and 9092 as appropriate; keep browser/API URLs and CORS settings aligned.
- **Production Nginx cannot start:** verify `SERVER_NAME`, certificate paths, host mount permissions, and that the certificate was issued before starting the overlay.
- **`scripts/verify.sh` fails with `EnvironmentTeardownError: Cannot load '/chunk-….js' … after the environment was torn down`:** an Angular spec started a lazy route import and did not await it, so the import resolved after Vitest tore the test environment down. It is not a product failure and not a failed assertion: the affected spec files report `0 test` and the run's own callstack names the spec that started the import. See the pitfall note in [`frontend/README.md`](frontend/README.md) for the cause and the fix.
