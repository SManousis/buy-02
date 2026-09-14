# Media Service

Media Service owns seller-uploaded image metadata and bytes. See the root [`README.md`](../README.md) for full-stack startup, public Gateway/Nginx paths, HTTPS, persistence, and audit procedures.

## Endpoints

Paths are service/Gateway paths; add `/api` when calling through Dockerized Nginx.

| Method | Path | Access | Response |
|---|---|---|---|
| `POST` | `/media/images` | SELLER | Multipart `file`; upload DTO, `201` |
| `GET` | `/media/images/{id}` | Public | Validated image bytes |
| `GET` | `/media/images/{id}/metadata` | SELLER + owner | `{ id, sellerId, contentType, sizeBytes }` |
| `DELETE` | `/media/images/{id}` | SELLER + owner | Permanent deletion, `204` |
| `GET` | `/actuator/health` | Public | Health status |
| `GET` | `/actuator/info` | Public | Info payload |

Upload returns `id`, `sellerId`, `originalFileName`, generated `storedFileName`, `contentType`, numeric `size`, configured public `url`, and `createdAt`. Owner metadata deliberately omits filenames and storage keys.

## Validation and security

- Upload and delete require a JWT with `role: SELLER`; delete and metadata also compare JWT `sub` with `sellerId`.
- JPEG, PNG, and WEBP are supported up to exactly 2 MiB and 16 million pixels.
- Declared MIME must match detected content. Image decoding, format structure, checksums/end markers, and trailing-content rules reject renamed, corrupt, and polyglot-like input.
- Served responses use the validated stored type, content length, safe inline filename, and a one-hour revalidating cache policy. `X-Content-Type-Options: nosniff` is not set by the controller: Spring Security's default header writers add it inside this service, so it is already present when an image is fetched straight through the Gateway, and Nginx adds it a second time on the browser path.
- JWT signature, expiry, issuer, and audience are checked locally with the shared HS256 settings.

## Storage and cleanup

MongoDB collection `mediaservice.media_assets` stores metadata and an internal storage key. The local filesystem implementation stores generated `.jpg`, `.png`, or `.webp` files below `MEDIA_STORAGE_PATH`; the Compose `media-storage` volume persists them.

If metadata save fails after writing bytes, Media Service attempts to remove the new file. Direct deletion removes bytes, metadata, then asynchronously publishes `image.deleted`. Product deletion is consumed from `product.deleted` and deletes each referenced image; already-missing assets are idempotent success.

These operations are not atomic across filesystem, MongoDB, Product Service, and Kafka. There is no transactional outbox. Exhausted publication/consumer retries, crashes, or validate-then-save races can leave orphan files or stale Product references requiring event replay or reconciliation. Replacing Product or avatar ID lists does not itself delete omitted Media.

The Product media page attempts to delete newly uploaded images when Product association fails, but browser/process termination can interrupt it. The avatar page does not issue equivalent client-side cleanup when its follow-up profile request fails before persistence. Both cases can leave unattached assets.

## Configuration

| Variable | Default / constraint |
|---|---|
| `SERVER_PORT` | `8083` |
| `MONGODB_URI` | `mongodb://localhost:27018/mediaservice` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `JWT_SECRET` | Required, at least 32 characters |
| `JWT_EXPIRATION_MS` | `86400000`, minimum 60000 |
| `JWT_ISSUER` | `user-service` |
| `JWT_AUDIENCE` | `buy-01-api` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` |
| `MEDIA_STORAGE_PATH` | `./media-storage` |
| `MEDIA_PUBLIC_BASE_URL` | `http://localhost:8080/media/images` |
| `EUREKA_CLIENT_ENABLED` | `false`; Compose enables registration |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/` |

For source mode, start reachable MongoDB and Kafka dependencies, set a strong shared JWT secret, and use the Maven wrapper:

```bash
export JWT_SECRET=replace-with-a-real-random-secret-at-least-32-characters
cd media-service
bash ./mvnw spring-boot:run
```

Product association and cleanup additionally require Product Service and compatible Kafka/JWT settings. Prefer the root Docker or hybrid development workflow when exercising end-to-end behavior.
