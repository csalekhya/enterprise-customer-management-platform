# Architecture

A deeper look at *why* the system is shaped the way it is. The README covers what it does; this
document covers the decisions, the trade-offs that were accepted, and where the design would
have to change under load.

---

## 1. Bounded contexts

The platform is one deployable, but internally it is four cohesive groups of behaviour. Each one
owns its invariants and is entered only through its service:

| Context | Aggregate root | Owns | Invariants it enforces |
|---|---|---|---|
| **Identity & Access** | `User` | users, roles, refresh tokens | credentials are only ever stored hashed; a role change invalidates the refresh chain; an admin cannot remove their own admin access |
| **Customer Book** | `Account` | accounts, customers | a contact always belongs to exactly one account; an account cannot be deleted while it has children; ownership transfer is a privileged operation |
| **Pipeline** | `Opportunity` | opportunities | a WON deal has probability 100 and a LOST deal has 0; amount is strictly positive |
| **Activity** | `Interaction` | interactions | append-only; an entry can never be logged in the future; only the author (or a manager) can remove one |

`Account` is the aggregate root of the Customer Book: contacts, deals and — transitively —
interactions all reach the database through an account identifier. That is why the physical
model leads almost every composite index with `account_id`.

**Why one deployable and not four services.** These contexts share a single transactional
question all the time — *"can this user change this row, and what does the dashboard say now
that they have?"* Splitting them would turn one `GROUP BY` into four network calls and one
ownership check into a distributed authorization problem, in exchange for independent
deployability that a four-person sales team does not need. The seam is drawn in the package
structure and the service boundaries, so extracting Activity into its own service later is a
mechanical change rather than a rewrite. That is the point of drawing the seam before you need
it.

---

## 2. Layering rules

```
web/        controllers, request/response records     — HTTP only, no business logic
service/    use cases, transactions, authorization    — the only layer that decides anything
repository/ Spring Data interfaces, aggregate queries — no business logic, no HTTP types
domain/     JPA entities and their enums              — no framework annotations beyond JPA
```

Four rules hold this in place, and they are the ones worth reviewing a pull request for:

1. **Entities never leave the service layer.** Everything crossing the HTTP boundary is a DTO
   record, mapped by MapStruct. An entity on the wire leaks the schema into the API contract,
   invites accidental lazy loading during serialisation, and makes every column rename a
   breaking change for the client.
2. **Controllers do not decide.** A handler validates its body, delegates to one service method,
   and shapes a response. The only authorization a controller expresses is `@PreAuthorize`, which
   is a declaration about the *route*, not about the data.
3. **Repositories do not know about HTTP.** They take `Pageable` and `Specification` — both Spring
   Data types — and never a DTO.
4. **Dependencies point inwards only.** `web → service → repository → domain`. A service never
   imports from `web` except its own DTO records, and `domain` imports nothing from the
   application at all.

The pay-off is testability. `CustomerServiceTest` instantiates the service with five mocks and
asserts the ownership rule without a Spring context, a servlet or a database — which is why that
test runs in milliseconds and gets run on every save.

---

## 3. Authorization: two checks, deliberately

Role-based access control answers *"may a SALES_REP call `PUT /customers/{id}` at all?"*. It
cannot answer *"may **this** rep edit **this** contact?"*, because every rep holds an identical
authority. Both questions have to be asked, in different places:

**At the route — `@PreAuthorize` on the handler.** Declarative, adjacent to the endpoint, and
impossible to drift out of sync with the mapping because it lives on the same method.

**At the row — an ownership check in the service.** The service has the entity in hand, so it can
compare `customer.ownerUser.id` with the caller's id. ADMIN and SALES_MANAGER bypass it
(`CrmUserDetails.canBypassOwnership()`); everyone else must own the record or gets
`ForbiddenOperationException` → HTTP 403.

Putting the row check in the service rather than the controller means it holds for every entry
point. A future CSV importer, a scheduled job or a GraphQL facade all inherit it for free. The UI
does hide buttons a role cannot use (`RoleGate`), but that is courtesy, not control — the API
refuses the request either way.

The authorization matrix:

| | Read | Create | Update own | Update any | Reassign owner | Delete | Manage users |
|---|---|---|---|---|---|---|---|
| ADMIN | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SALES_MANAGER | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| SALES_REP | ✅ | ✅ | ✅ | ❌ | ❌ | own only | ❌ |
| READ_ONLY | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

---

## 4. Specification over QueryDSL (and over string concatenation)

The contacts endpoint accepts six independent, optional filters. There are 64 combinations, and
there are three ways to serve them:

**Repository method per combination.** `findByAccountIdAndLifecycleStage`,
`findByAccountIdAndLifecycleStageAndOwnerUserId`, and so on. Unmaintainable past about three
filters, and adding a seventh doubles the surface.

**Concatenated JPQL.** A `StringBuilder` of `and ...` clauses. Compact, and the direct route to
an injection vulnerability the first time someone appends a value instead of binding a
parameter. It also loses compile-time knowledge of the entity graph entirely.

**JPA Criteria via `Specification`.** Each filter is a function returning a `Predicate` or `null`;
`build()` folds the non-null ones with `AND`. Adding a filter is one small static method. Every
literal is bound as a JDBC parameter by construction, so dynamic filtering cannot become string
interpolation. And because `JpaSpecificationExecutor#findAll(Specification, Pageable)` produces
both the page query and its `COUNT`, pagination stays correct as filters change.

**Why not QueryDSL**, which is nicer to read? It needs an annotation processor generating `Q`
classes, and this build already runs two processors in a specific order (Lombok before MapStruct)
— a third is real cost in build complexity and IDE setup. QueryDSL's advantages are strongest for
complex projections and joins; this schema's queries are shallow, single-entity predicates where
Criteria is verbose but adequate. The verbosity is confined to two files that rarely change.
Different schema, different answer: heavy reporting joins would justify QueryDSL, and the
specification classes are the isolated seam where that swap would happen.

---

## 5. Transaction boundaries

**One public service method is one transaction.** The class carries
`@Transactional(readOnly = true)` and mutating methods override it with `@Transactional`.

- **Reads are `readOnly`.** Hibernate skips dirty checking on flush and the connection is marked
  read-only, which lets a driver or proxy route it to a replica when one exists.
- **Writes rely on the managed entity, not on `save()`.** Inside the transaction, mutating an
  entity loaded through the repository is enough — the dirty check flushes it at commit. The
  explicit `save()` calls in this codebase are on *new* entities, where they are meaningful.
- **No transaction spans an HTTP call.** With `open-in-view: false`, the session closes when the
  service method returns, so serialisation happens against fully-initialised DTOs. This is the
  single most valuable setting in `application.yml`: it converts "mystery lazy-load during JSON
  rendering, on the connection we already returned to the pool" into a `LazyInitializationException`
  at development time, which is a bug you can fix.
- **Validation before mutation.** Uniqueness probes (`existsByEmailIgnoreCase`) and ownership
  checks run before anything is written, so a rejected request never leaves a partial change to
  roll back.
- **Cache eviction is explicit, not transactional.** `DashboardService.invalidate()` is called
  from the controller after a pipeline write succeeds. If the transaction rolls back the
  controller never reaches it, and a stale cache entry would only outlive the truth by its
  60-second TTL anyway.

---

## 6. Caching strategy

Two Caffeine caches with different TTLs, because they answer different questions:

| Cache | TTL | Contents | Why this TTL |
|---|---|---|---|
| `dashboardSummary` | 60 s | the whole KPI bundle | the most-requested endpoint in the product; a minute is invisible to a human reading revenue figures but collapses a login stampede into one database hit per instance |
| `referenceData` | 30 min | owner-picker options | the user directory of a sales organisation changes a few times a quarter |

**Why in-process and not Redis.** The payloads are kilobytes, they tolerate per-instance
divergence for their TTL, and a network hop to a cache would cost more than the aggregate query
it replaces. Caffeine adds no operational surface at all — no cluster, no failover, no eviction
tuning at this size.

**What the trade-off actually is.** With four API tasks, four copies of the summary can be up to
60 seconds apart from each other. For KPI tiles that is fine. It would *not* be fine for anything
a user acts on transactionally, which is why nothing transactional is cached.

**Where this breaks.** The moment a cached value must be identical across instances or
invalidated globally on write, `CacheConfig` becomes a Redis `CacheManager` and the
`@Cacheable`/`@CacheEvict` annotations stay exactly as they are. That is the whole reason the
cache is reached through Spring's abstraction rather than a Caffeine handle held in a service.
`DashboardDtos.Summary` is already `Serializable` for the same reason.

---

## 7. Token rotation

**Access token: 15 minutes, self-contained, never stored.** Authorizing a request is signature
verification plus claim parsing — no database round trip, which is what allows the API to scale
horizontally without a shared session store. The price of statelessness is that an access token
cannot be recalled, so it is short-lived and lives only in browser memory. A `localStorage` copy
would survive XSS; memory does not survive a page reload, which is precisely what the refresh
token is for.

**Refresh token: 7 days, rotating, stored as a hash.** This is the credential worth stealing, so
it gets the protection:

1. Only `SHA-256(token)` is persisted. A database dump cannot be replayed as a session.
   BCrypt would be wrong here — the token is already 200+ bits of entropy, so a slow KDF buys
   nothing and costs a hash on every refresh.
2. Every successful refresh revokes the presented token and issues a new pair. A stolen token
   therefore works at most once.
3. Presenting an already-revoked token revokes the *entire* chain for that user. Either the
   attacker used it and the legitimate user's next refresh trips the alarm, or the reverse —
   either way both parties are logged out and the compromise becomes a visible event rather than
   a silent week.
4. The `typ` claim distinguishes the two kinds and is asserted on parse, so an access token can
   never be presented to `/auth/refresh` and a refresh token can never be used as a bearer
   credential.
5. A role change calls `revokeAllForUser`. Authorities are baked into issued access tokens, so
   without this a demoted user would keep their old permissions until expiry.

**The client half.** One axios response interceptor owns recovery. On a 401 it parks the failing
request, refreshes once, and replays every parked request with the new token. Without the queue,
five concurrent expired requests would trigger five rotations, each revoking the previous one —
and the fifth would log the user out. That queue is the difference between silent refresh and an
intermittent, unreproducible logout bug.

A 403 is deliberately *not* retried: the caller is authenticated and simply not allowed, so a new
token changes nothing and a retry only doubles the audit noise.

---

## 8. Data access and the N+1 problem

CRM screens are exactly the shape that produces N+1 queries: a list of rows, each showing a field
from a related entity. Four defences, in order of how early they apply:

1. **Entity graphs on the read paths.** `@EntityGraph(attributePaths = {"account", "ownerUser"})`
   on the specification `findAll` makes the associations a join in the same statement. A 25-row
   contact page is one query, not 51.
2. **`open-in-view: false`.** No lazy association can be touched during serialisation, because
   the session is already closed.
3. **`default_batch_fetch_size: 25`.** Where a lazy collection *is* touched inside a transaction,
   Hibernate fetches it for 25 parents at once with an `IN (...)` — turning N queries into N/25.
4. **Aggregates for counts, never collections.** `AccountDtos.Response.withCounts()` takes two
   `COUNT` results. Mapping a `@OneToMany` to produce those numbers would load every child row to
   print an integer.

The dashboard is the clearest case. Nine statements serve the entire screen, and that number is
constant in the size of the dataset: one `GROUP BY` per chart, a `LIMIT`ed top-accounts query and
five `COUNT`s. The
naive version — iterate the stages, sum each one; iterate the lifecycle stages, count each one —
grows with the enums and reads the whole pipeline into the heap.

---

## 9. Scaling path

The current shape comfortably handles a mid-market sales organisation: tens of thousands of
accounts, hundreds of thousands of contacts, a handful of concurrent users per instance. Where
it strains, in the order the strain arrives:

**1. Read volume on the dashboard (first to bite).** Symptom: RDS CPU tracking dashboard traffic.
Fix: point read-only transactions at an RDS read replica. `@Transactional(readOnly = true)` is
already on every read path, so this is a routing datasource, not an application rewrite.

**2. Cache divergence across instances.** Symptom: users comparing numbers between tabs and
disagreeing. Fix: swap `CacheConfig` to a Redis `CacheManager`. Annotations unchanged.

**3. Contact search.** Symptom: `LIKE '%fragment%'` on `customers` stops using any index once the
table is large — a leading wildcard cannot. Fix: a MySQL `FULLTEXT` index for the common case, or
OpenSearch if fuzzy matching and relevance ranking are wanted. The search predicate is one method
in `CustomerSpecifications`, which is where that change lands.

**4. Interaction table growth.** Symptom: the activity table dominates storage and the timeline
slows down. Fix: partition `interactions` by `occurred_at` range, archiving cold partitions to
S3. The API already pages every timeline and never scans the table without a `customer_id` or a
date window.

**5. True multi-tenancy.** The ownership column is a per-user boundary, not a per-tenant one. Real
tenancy needs a `tenant_id` on every table, a Hibernate filter applied from the JWT, and a
migration — which is exactly why `ownerUser` was not overloaded to mean "tenant". Conflating the
two is the mistake that makes the migration expensive later.

**6. Write throughput.** Last to matter, because CRM writes are human-paced. When it does: the
Activity context is the natural extraction — append-only, no shared invariants with the rest, and
its own table.

---

## 10. Things deliberately left out

Each of these was considered and skipped, because it would add operational or conceptual weight
without answering a question the product actually has:

- **Event sourcing for the audit trail.** `BaseAuditEntity` plus append-only interactions answers
  every question the product actually asks. Event sourcing would answer questions nobody asked
  and make the simplest report a projection.
- **A service mesh.** Two services behind one load balancer do not need mTLS between them, traffic
  shifting, or a sidecar per task.
- **GraphQL.** The client is one SPA whose screens map one-to-one onto REST resources. Adding a
  schema layer would add a query planner and an N+1 surface for zero flexibility gained.
- **Kubernetes.** ECS Fargate runs two stateless services with rolling deploys, autoscaling and a
  circuit breaker in ~300 lines of Terraform. EKS would add a control plane, a node group and an
  ingress controller to reach the same place.
