# Phase 5 Workflow — Profile Analytics

This workflow is for implementing Phase 5 of `buy02Plan.md` on the branch
`feature-phase5-profile-analytics`.

The work extends the existing `order-service` and the existing Angular
`profile` module. It does not create a new microservice, a new collection, or
any new field on `User` or `Product`: every statistic is computed from the
order history that `order-service` already owns (`buy02Plan.md` §2.4, §3.4).

**The whole phase ships as one commit and one pull request.** The steps below
are working checkpoints, not commits. See §4 for how to keep that single commit
safe while working.

## 1. Phase 5 outcome

A buyer must be able to see, on their profile page:

1. Their total amount spent.
2. Their top products — ranked by money spent on each product.
3. Their most-bought products — ranked by quantity bought.

A seller must be able to see, on their profile page:

1. Their total revenue.
2. Their best-selling products — ranked by quantity sold, with the revenue
   each product brought in.

Every figure must be computed only from the caller's own orders, and must
exclude cancelled orders.

## 2. Dependencies and rules

### Dependencies

- **Phase 3 must be merged into `main` first.** Phase 5 reads the `Order`
  model, statuses, and item snapshots that Phase 3 introduced.
- **Phase 4 is not required.** Phase 5 does not use product search or
  filtering. The two phases only touch some of the same files
  (`OrderController`, `frontend/src/app/shared/services/order.ts`), so if they
  are developed in parallel, merge one before rebasing the other.

### Communication

- **Synchronous REST only.** The frontend calls two new `GET` endpoints
  through the Gateway.
- **No calls to `product-service`.** Order items already snapshot
  `productId`, `name`, `unitPrice`, `quantity`, and `imageId`, so product names
  and images come from the orders themselves. This also means a product that
  was later renamed or deleted still appears correctly in historical stats.
- **No Kafka.** Reading statistics changes no state, so no event is published
  and no topic is added.

### Business rules to confirm before Step 1

These defaults follow `buy02Plan.md`. Confirm them with your teammate before
implementing, because changing them later changes every test.

| Rule | Default |
|---|---|
| Which orders count | Every status except `CANCELLED` |
| Seller revenue | Sum of non-cancelled order subtotals (not only `DELIVERED`) |
| "Top products" (buyer) | Ranked by amount spent (`unitPrice × quantity`, summed) |
| "Most-bought products" (buyer) | Ranked by total quantity |
| "Best-selling products" (seller) | Ranked by total quantity sold |
| Tie-breaking | Higher amount first, then product name alphabetically |
| List length | Top 5 per list |
| Product name shown | The name from the most recent order containing that product |
| Money precision | `BigDecimal` end to end; never `double` |

## 3. Implementation sequence

### Step 0 — Establish the baseline

- Confirm PR #9 (Phase 3) is merged, then run `git switch main` and
  `git pull origin main`. (Commands in this file use `origin` for GitHub; if
  your clone names it `github`, as in `buy02Plan.md`, substitute that name.)
- Create the branch: `git switch -c feature-phase5-profile-analytics`.
- Confirm the working tree is clean.
- Run the existing test suites and record the counts:
  - `order-service`: `.\mvnw.cmd test` (from `order-service/`)
  - Frontend: `npm test -- --watch=false` (from `frontend/`)
- Read `OrderController`, `OrderQueryService`, `OrderRepository`, `Order`,
  `OrderItem`, `OrderQueryServiceTest`, and the `profile` page before editing.
- Confirm the business rules in §2.

Checkpoint: both suites pass and the rules in §2 are agreed.

### Step 1 — Add the repository queries

In `order-service/.../repository/OrderRepository.java`, add derived queries
that already exclude cancelled orders:

- `List<Order> findByBuyerIdAndStatusNotOrderByCreatedAtDesc(String buyerId, OrderStatus status)`
- `List<Order> findBySellerIdAndStatusNotOrderByCreatedAtDesc(String sellerId, OrderStatus status)`

Newest-first ordering means the first snapshot seen for a product is its
latest name and image. The existing `buyerId` and `sellerId` indexes cover
both queries; no new index is needed.

Design decision — compute in Java, not in a Mongo aggregation pipeline:

`buy02Plan.md` §3.4 suggests a `$match → $unwind → $group` pipeline. This phase
instead loads the caller's non-cancelled orders and groups them in a plain Java
calculator (Step 2), because:

- `order-service` has no `MongoTemplate` usage yet, and its tests are plain
  Mockito unit tests with no database; a Java calculator is fully unit-testable
  in that style, while a pipeline would need a running MongoDB to test.
- `BigDecimal` arithmetic stays exact in Java without `Decimal128` conversion.
- One user's order history is small, and the query is already scoped by an
  indexed `buyerId`/`sellerId`.

Record this deviation in `buy02Plan.md` (see Step 7). Revisit it only if a
single user's order history grows large enough for the query to be slow.

Checkpoint: `order-service` compiles and the existing tests still pass.

### Step 2 — Add the statistics calculator and DTOs

In `order-service`, add response DTOs under `dto/`:

- `ProductStat(String productId, String name, String imageId, int quantity, BigDecimal amount)`
- `BuyerStatsResponse(List<ProductStat> topProducts, List<ProductStat> mostBoughtProducts, BigDecimal totalSpent, int orderCount)`
- `SellerStatsResponse(List<ProductStat> bestSellingProducts, BigDecimal totalRevenue, int orderCount)`

Add `service/OrderStatsService.java`:

- `BuyerStatsResponse buyerStats(String buyerId)`
- `SellerStatsResponse sellerStats(String sellerId)`

Both methods call the Step 1 queries with `OrderStatus.CANCELLED`, then share
one private grouping routine: flatten all items, group by `productId`, sum
`quantity` and `unitPrice × quantity`, keep the most recent name/image, sort by
the rules in §2, and keep the top 5.

Totals are the sum of order `subtotal` values, starting from `BigDecimal.ZERO`,
so a user with no orders gets `0`, `0` orders, and empty lists — never `null`
and never an error.

Add `OrderStatsServiceTest` next to `OrderQueryServiceTest`, in the same
Mockito style. Cover:

- No orders → zero totals and empty lists.
- Cancelled orders are excluded (the repository is called with `CANCELLED`).
- The same product across several orders is merged into one entry.
- "Top" (by amount) and "most bought" (by quantity) produce different
  rankings for the same data.
- Ties follow the tie-break rule.
- Only the top 5 are returned when there are more than 5 products.
- The most recent snapshot name is used after a rename.
- Totals are exact (`BigDecimal` values such as `19.99 × 3`).

Checkpoint: `OrderStatsServiceTest` passes and covers every rule in §2.

### Step 3 — Add the endpoints

In `OrderController`, inject `OrderStatsService` and add:

| Method | Path | Access | Returns |
|---|---|---|---|
| `GET` | `/orders/stats/me` | Authenticated | `BuyerStatsResponse` for `jwt.getSubject()` |
| `GET` | `/orders/stats/selling` | `SELLER` (existing `ensureSeller`) | `SellerStatsResponse` for `jwt.getSubject()` |

Rules:

- The user ID always comes from the JWT subject. Never accept a `userId` query
  parameter — that would let one user read another user's figures.
- A non-seller calling `/orders/stats/selling` gets `403` from the existing
  `AccessDeniedException` handling.
- No route change is needed: the Gateway's `order-service-route` already
  matches `/orders/**`, and `/orders/{id}` only matches a single path segment,
  so `/orders/stats/me` does not collide with it.

Checkpoint: `.\mvnw.cmd test` passes in `order-service`, and with the stack
running, `GET http://localhost:8080/orders/stats/me` returns `401` without a
token and `200` with one.

### Step 4 — Add the frontend service methods

In `frontend/src/app/shared/services/order.ts`:

- Add `ProductStat`, `BuyerStats`, and `SellerStats` interfaces matching the
  DTOs from Step 2.
- Add `statsMine(): Observable<BuyerStats>` → `GET ${base}/stats/me`.
- Add `statsSelling(): Observable<SellerStats>` → `GET ${base}/stats/selling`.

Extend `order.spec.ts` to assert both URLs, in the same style as the existing
service tests.

Checkpoint: `npm test -- --watch=false` passes.

### Step 5 — Add the analytics panels

In the `profile` module, add two presentational components:

- `profile/components/buyer-stats/` — total spent, a "Top products" list, and
  a "Most bought" list.
- `profile/components/seller-stats/` — total revenue and a "Best-selling
  products" list with quantity and revenue per product.

On the `profile` page, show `seller-stats` when `isSeller` is true and
`buyer-stats` otherwise. Sellers cannot add products to a cart
(`PHASE3_WORKFLOW.md` §6), so they have no purchase history to show.

Reuse the shared pieces introduced during the Phase 3 SonarQube cleanup
instead of copying loading logic again — copying it is what failed the
duplication gate on PR #9:

- Extend `LoadablePageBase<BuyerStats>` / `LoadablePageBase<SellerStats>` for
  loading, error, timeout, and teardown.
- Use `<app-load-state>` for the spinner and the "could not be loaded / Try
  again" panel.
- Import `SharedModule` into `ProfileModule`.

UI rules:

- Money uses the existing `currency:'EUR'` pipe, like the cart and orders
  pages.
- Empty history shows a friendly empty state ("No purchases yet" / "No sales
  yet") instead of `€0.00` next to empty lists.
- Each product row links to `/products/{productId}`; a deleted product still
  shows its snapshot name.
- Lists stack to one column on narrow screens.
- A failed stats request must not break the rest of the profile page — the
  username and avatar forms stay usable.

Add specs for both components covering: loading state, rendered figures,
empty state, and error state with a working retry. Extend `profile.spec.ts` to
check that a seller sees the seller panel and a buyer sees the buyer panel.

Checkpoint: all frontend specs pass and both panels render correctly with
`npm start` against the running stack.

### Step 6 — Quality gate before committing

Run everything locally before creating the commit, so the single commit goes
green on the first CI run:

1. `order-service`: `.\mvnw.cmd test`
2. Frontend: `npm run build`, then `npm run test:coverage`
3. Local SonarQube (see `SONAR_QUICKSTART.md`): run the frontend and backend
   scans with `-Dsonar.qualitygate.wait=true` and confirm:
   - Coverage on new code ≥ 80%
   - Duplicated lines on new code ≤ 3%
   - No new bugs, vulnerabilities, or unreviewed security hotspots
   - Maintainability rating A (fix code smells shown in the IDE by SonarLint)
4. Rebuild and start the stack: `docker compose up -d --build`, then run the
   acceptance scenario in §5.

Checkpoint: all tests pass, the local quality gate passes, and the acceptance
scenario passes in the running app.

### Step 7 — Update the plan and create the single commit

Update `buy02Plan.md` in the same commit:

- Tick the three Phase 5 checkboxes.
- Add a note under Phase 5 that statistics are computed in Java over indexed
  per-user queries instead of a Mongo aggregation pipeline, with the reason
  from Step 1.

Stage and review everything before committing:

```powershell
git status
git diff --stat
git add order-service frontend buy02Plan.md PHASE5_WORKFLOW.md
git status   # confirm no coverage/, .scannerwork/, or dist/ files are staged
```

Create the one commit:

```text
feat(analytics): add buyer and seller profile statistics

Add GET /orders/stats/me (total spent, top products by spend, most-bought
products by quantity) and GET /orders/stats/selling (total revenue,
best-selling products) to order-service, computed from the caller's
non-cancelled orders using the item snapshots stored at checkout.

Show the matching analytics panel on the profile page: buyers see their
spending, sellers see their sales. Both panels reuse LoadablePageBase and
app-load-state.

Statistics are grouped in Java over indexed per-user queries rather than a
Mongo aggregation pipeline so the logic stays unit-testable with the
existing Mockito setup; recorded in buy02Plan.md.
```

Push and open the PR:

```powershell
git push -u origin feature-phase5-profile-analytics
```

If a Gitea remote is configured (`git remote -v`), push the branch there too,
as `buy02Plan.md`'s handoff procedure requires.

Open the PR into `main`, wait for Jenkins and the `build-and-analyze`
SonarQube check, request review, and merge only when green and approved.

## 4. Working safely with a single commit

One commit means there are no saved restore points on the remote while you
work. To avoid losing work:

- **Option A — do not commit until Step 7.** Use `git stash push -m "phase5 wip"`
  if you need to switch branches, and `git stash pop` to continue.
- **Option B — commit locally, squash before pushing.** Make as many local
  `wip:` commits as you like, then before the first push collapse them into
  one:

  ```powershell
  git reset --soft main
  git commit   # write the message from Step 7
  ```

  Nothing is pushed until the history is one commit, so the PR still shows a
  single commit.

If CI fails after the push, fix it and amend the same commit
(`git commit --amend`, then `git push --force-with-lease`) so the PR stays one
commit. Only force-push your own feature branch — never `main`.

## 5. Required acceptance scenario

Use two sellers, two buyers, and at least three products with known prices.

1. Seller A creates product P1 (€10.00) and P2 (€25.00); seller B creates
   P3 (€5.00).
2. Buyer 1 checks out 3 × P1 and 1 × P3 → orders total €30.00 (A) and
   €5.00 (B).
3. Buyer 1 checks out 1 × P2 → order total €25.00 (A).
4. Buyer 1 checks out 2 × P3 → order total €10.00 (B), then cancels it.
5. Buyer 2 checks out 1 × P1 → order total €10.00 (A).
6. Buyer 1's profile shows total spent **€60.00** (30 + 5 + 25; the
   cancelled €10.00 is excluded).
7. Buyer 1's top products by spend: P1 (€30.00), P2 (€25.00), P3 (€5.00).
8. Buyer 1's most-bought by quantity: P1 (3), then P2 and P3 (1 each, P2
   first because it has the higher amount).
9. Seller A's profile shows total revenue **€65.00** (30 + 25 + 10) and
   best-selling P1 (4 sold), then P2 (1 sold).
10. Seller B's profile shows total revenue **€5.00** — the cancelled order is
    excluded — and only P3 (1 sold).
11. Seller B does not see any of seller A's products or revenue.
12. Buyer 2 cannot see buyer 1's figures: there is no parameter to request
    another user's stats.
13. A buyer calling `GET /orders/stats/selling` directly receives `403`.
14. A request without a token receives `401`.
15. A brand-new buyer and a brand-new seller see the empty state, not an
    error.
16. Stopping `order-service` shows the panel's error state with **Try again**
    while the rest of the profile page still works; restarting it and clicking
    **Try again** loads the figures.
17. Renaming P1 after the orders were placed still shows the stats correctly,
    under the latest snapshot name from the orders.

## 6. Definition of done

Phase 5 is complete only when:

- `GET /orders/stats/me` and `GET /orders/stats/selling` return correct
  figures from non-cancelled orders only.
- Stats are always scoped to the JWT subject; role rules are enforced.
- Buyer and seller panels are shown on the profile page with loading, empty,
  and error states.
- `OrderStatsServiceTest` and the new frontend specs pass, along with all
  existing tests.
- The local SonarQube quality gate passes before the push.
- The acceptance scenario in §5 passes end to end.
- The work is one commit on `feature-phase5-profile-analytics`, the PR is
  approved, Jenkins and `build-and-analyze` are green, and it is merged into
  `main`.
- The Phase 5 status and the aggregation design note in `buy02Plan.md` are
  updated accurately.

## 7. Verification record and design notes

Recorded when Step 6 was executed on `feature-phase5-profile-analytics`.

### What was verified

- Baseline before changes: `order-service` 37/37, frontend 145/145.
- `order-service` tests: **50/50** passing (`.\mvnw.cmd test`), adding
  `OrderStatsServiceTest` (10) and `OrderControllerStatsTest` (3).
- Frontend tests: **158/158** passing across 29 files, adding the two
  `order.spec.ts` stats tests, `buyer-stats.spec.ts` (5),
  `seller-stats.spec.ts` (4), and two role tests in `profile.spec.ts`.
- `npm run build` succeeds.
- Local SonarQube quality gates:
  - `buy-01-frontend`: **PASSED** — coverage on new code 85.3%,
    duplication on new code 0.42%, 0 open issues.
  - `buy-01-order-service`: **PASSED** — `OrderStatsService` and the stats
    DTOs at 100% coverage, 0% duplication, no new issues.
- Frontend duplication scan (jscpd, 100-token minimum, TS + HTML): no clones
  in Phase 5 files.
- Acceptance scenario §5, run against the Gateway with
  `docker compose up -d --build`: steps 1–15 and 17 **passed** with the
  exact expected figures (buyer 1 €60.00, seller A €65.00, seller B €5.00,
  403 for a buyer on seller stats, 401 without a token, zeros for new
  users, latest snapshot name after a rename). Step 16 (panel error state
  and retry) is covered by the component specs.

### Fixed during this phase

- The Maven Sonar plugin against the local SonarQube 9.9 server needs the
  token passed as `-Dsonar.login=...`; `-Dsonar.token=...` is rejected as
  "Not authorized". This only affects local scans, not the CI workflow.

### Known limitations / deviations

- Statistics are computed in Java rather than a Mongo aggregation pipeline
  (see Step 1). Recorded in `buy02Plan.md`.
- All business rules in §2 were implemented with their defaults.
- Pre-existing SonarQube findings in `order-service` files this phase did not
  touch remain open: 5 code smells (`Order`, `GlobalExceptionHandler`,
  `CartService`, `SecurityConfig`) and 1 CSRF security hotspot in
  `SecurityConfig` (CSRF is disabled for the stateless JWT API). They are
  candidates for the Phase 6 SonarQube pass.
