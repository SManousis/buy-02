# Frontend

Angular 21 frontend for Buy-01. The root [`README.md`](../README.md) is the authoritative full-stack, routing, API, HTTPS, and audit guide.

## Development

Use Node.js 22 and npm 10. Run the backend stack without its `frontend` service, then start Angular with hot reload:

```bash
cd frontend
npm ci
npm start
```

Open `http://localhost:4200`. The development environment calls `http://localhost:8080` directly, so the Gateway must be reachable and `CORS_ALLOWED_ORIGINS` must include exactly `http://localhost:4200`.

The production build instead uses same-origin `/api`. Container Nginx proxies `/api/*` to Gateway after removing `/api`, and serves Angular routes through the client-side fallback.

## Application routes

| Path | Access | Feature |
|---|---|---|
| `/`, `/products`, `/products/:id` | Public | Catalog and Product detail |
| `/auth/login`, `/auth/register` | Public | Email login and CLIENT/SELLER signup |
| `/profile` | Authenticated | Profile and SELLER avatar workflow |
| `/seller` | SELLER | Own-product dashboard |
| `/seller/products/new` | SELLER | Create Product |
| `/seller/products/edit/:id` | SELLER | Edit Product |
| `/seller/products/:id/media` | SELLER | Upload, associate, disassociate, and delete images |

Browser guards are navigation aids. Gateway and downstream services enforce actual JWT role and ownership rules.

## API representation

- Login sends `{ email, password }`.
- Profiles store `avatarMediaId`; the UI constructs `/media/images/{id}` from the environment API base.
- Products use canonical `imageIds`. The reader temporarily accepts legacy `imageUrls`, while all writes use `imageIds`.
- Upload responses use numeric `size` (not `sizeBytes`) and include `url`; rendering uses the current API base plus Media ID.

The product-media flow keeps upload/attach/failed-attach cleanup subscriptions alive across route destruction while suppressing updates to a destroyed view. Compensation remains best effort: closing the browser or terminating the process can leave an uploaded, unattached Media asset for operational cleanup.

The avatar flow uploads first and then updates the profile. It does not issue client-side Media deletion if that follow-up fails before User Service persistence, so that case can also leave an unattached asset.

## Tests and builds

```bash
cd frontend
npm ci
npm test -- --watch=false
npm run build
npm audit --omit=dev
```

There is no configured end-to-end test target. Use the root `bash scripts/verify.sh` command for the complete automated suite, then execute the manual multi-role/media/HTTPS audit in [`PLAN.md`](../PLAN.md) against a running stack.

One pitfall when writing specs: a spec that imports `AppModule` also gets `AppRoutingModule`, and the root router's initial navigation starts the lazy `loadChildren` import for the `''` route. If the spec does not await it, the import can resolve after Vitest has torn the test environment down, and the run fails with `EnvironmentTeardownError: Cannot load '/chunk-*.js' ...` attributed to unrelated spec files that happen to be loading at that moment — reported as suites with `0 test` rather than as a failed assertion. `src/app/layout/header/header.spec.ts` shows the fix: `await TestBed.inject(Router).navigate(['/'])` before creating the fixture. This is most likely to appear right after `npm ci`, when a cold dependency tree widens the window.
