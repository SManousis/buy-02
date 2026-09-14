# User Service architecture

The User Service owns registration, email/password authentication, JWT issuance, and the authenticated user's profile. The repository's root [`README.md`](../README.md) is the authoritative setup and public-routing guide; this file records service-specific design decisions.

## Responsibilities and dependencies

```text
AuthController (/auth/register, /auth/login)
UserController (/me)
        |
        v
UserService -----> UserRepository -----> userservice.users
    |                    MongoDB
    +--------> JwtService (HS256 token issuance)
    +--------> MediaOwnershipClient ---- Eureka/load balancer ---> media-service
```

The service does not publish or consume Kafka events. Avatar assignment is its only synchronous cross-service workflow.

## Package roles

| Package | Responsibility |
|---|---|
| `controller` | HTTP mapping and Jakarta validation entry points |
| `dto` | Public request/response shapes; profile responses exclude passwords |
| `service` | Registration, login, profile, avatar, and compensation rules |
| `security` | Stateless Spring Security, BCrypt, JWT encode/decode, role mapping |
| `client` | Owner-scoped Media metadata and deletion calls |
| `model` / `repository` | MongoDB `users` document and access methods |
| `config` | Validated properties, CORS, REST timeouts, correlation IDs |
| `exception` | Safe JSON error mapping |

## User document

`User` is a Spring Data MongoDB `@Document(collection = "users")`:

| Field | Meaning |
|---|---|
| `id` | MongoDB-generated ID and JWT subject |
| `username` | Unique, trimmed display/login-independent name |
| `email` | Unique, lowercased and trimmed authentication identifier |
| `password` | BCrypt hash, excluded from `toString` and every response DTO |
| `role` | `CLIENT` or `SELLER` |
| `avatarMediaId` | Optional application-level reference to a Media asset |
| `version` | Optimistic-lock version |
| `createdAt`, `updatedAt` | Mongo auditing timestamps |

`@Indexed(unique = true)` backs username/email uniqueness; the service also performs friendly pre-save checks, while `DuplicateKeyException` handles races. Passwords are encoded with BCrypt strength 12.

## Authentication contract

Registration accepts `{ username, email, password, role }`. Login accepts `{ email, password }`; username login is not implemented. Both return `{ token, userId, username, role }`.

Issued HS256 tokens contain:

- `iss` from `JWT_ISSUER`
- `aud` from `JWT_AUDIENCE`
- `sub` equal to User ID
- `username`, `email`, and `role` custom claims
- `iat` and `exp`; lifetime comes from `JWT_EXPIRATION_MS`

User, Gateway, Product, and Media must share the same JWT secret/issuer/audience. Incoming protected requests validate signature, expiry, issuer, and audience. The service is stateless and maps `role` to a `ROLE_` authority.

## Profile and avatar rules

`GET /me` returns `{ id, username, email, role, avatarMediaId, createdAt }`. `PUT /me` accepts optional `username`, `avatarMediaId`, and `removeAvatar` fields.

- Only a SELLER can assign `avatarMediaId`.
- Media metadata must identify the same Media ID, JWT subject as owner, and an image content type.
- Sending `removeAvatar: true` and an avatar ID together is invalid.
- Removing or replacing the reference does not delete the old Media asset.
- Media discovery/transport/5xx failure becomes `503`; invalid/not-owned Media becomes a safe `400` avatar-reference response.
- Media calls use 2-second connect and 3-second read timeouts.
- If persistence fails after selecting a genuinely different avatar, the service attempts to delete that newly assigned Media asset as compensation. Resubmitting the currently referenced ID never makes it a cleanup target. Compensation is best effort, not a distributed transaction.

## Security and errors

Public endpoints are registration, login, `GET /actuator/health`, `GET /actuator/info`, and CORS preflight. Everything else requires a valid JWT. Correlation IDs are accepted/generated, logged, returned as `X-Correlation-ID`, and included in JSON errors.

Expected errors include field-level `400`, login `401`, authenticated authorization `403`, missing User `404`, duplicate email/username `409`, Media validation `503`, and safe `500`. Internal exception traces are logged, while Spring's raw error message and stack trace are disabled in responses.

## Configuration

| Variable | Default / constraint |
|---|---|
| `SERVER_PORT` | `8081` |
| `MONGODB_URI` | `mongodb://localhost:27018/userservice` |
| `JWT_SECRET` | Required, at least 32 characters |
| `JWT_EXPIRATION_MS` | `86400000`, minimum 60000 |
| `JWT_ISSUER` | `user-service` |
| `JWT_AUDIENCE` | `buy-01-api` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |
| `MEDIA_SERVICE_NAME` | `media-service` |
| `EUREKA_CLIENT_ENABLED` | `false`; enable for avatar Media lookup |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/` |

For the supported Docker and source-development workflows, see the root [`README.md`](../README.md). Running User Service alone supports registration/login/profile reads, but avatar assignment additionally requires Eureka and Media Service.
