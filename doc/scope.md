# Gatekeeper — Project Scope (Source of Truth)

> This document is the **single source of truth** for the Gatekeeper service.
> Code, migrations, tests, and deployment must conform to what is written here.
> If reality has to diverge from this document, change this document first, then the code.
>
> Task breakdown lives in [`session.md`](./session.md). Every task there references a `§` section here.

---

## 1. Product Overview

**Gatekeeper** is a production-grade, secure **monolithic** service that manages hierarchical
permissions, organization tenancy, and runtime feature toggling — all without restarting
services.

Three pillars:

1. **Tenant Isolation** — every row of tenant-owned data carries a `tenant_id`; Hibernate filters
   apply the tenant predicate automatically so no query can read across tenants.
2. **Granular RBAC/ABAC** — roles inherit from other roles; permissions are resolved through the
   inheritance graph and enforced at the **method level** with `@PreAuthorize` plus a custom
   security expression handler. Attribute-based rules layer on top of role-based ones.
3. **Dynamic Feature Flags** — an evaluation engine supporting environment overrides, user
   whitelists, and deterministic percentage rollouts, mutable at runtime through the API.

Cross-cutting engineering focus (the reason this project exists):

- **Cache consistency** — cache-aside on Redis for permission lookups, with a mutex-based
  stampede guard and event-driven invalidation.
- **Query performance** — recursive CTEs for role inheritance, backed by composite indexes,
  with `EXPLAIN ANALYZE` evidence recorded in §13.
- **Automated integration testing** — Testcontainers suite proving auth failure, token
  expiration, and (critically) **absence of tenant leakage**.

### Non-goals

- No web UI / admin console (API + Swagger only).
- No billing, metering, or quota enforcement.
- No multi-region or cross-region replication; one instance, one region.
- No external identity provider — Gatekeeper issues its own tokens.
- No microservice split. This is deliberately one deployable.

---

## 2. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Language | **Java 21** (LTS) | Virtual threads, records, sealed types, pattern matching. `pom.xml` currently targets `24` — **must be corrected to 21** (Boot 3.3 + Koyeb runtime images). |
| Framework | **Spring Boot 3.3.x** via `spring-boot-starter-parent` | Managed dependency versions; Jakarta EE 10 baseline. |
| Web | `spring-boot-starter-web` | Blocking MVC + virtual-thread executor (`spring.threads.virtual.enabled=true`). |
| Persistence | `spring-boot-starter-data-jpa` (Hibernate 6.5) | `@Filter`-based multi-tenancy, native CTE queries. |
| Validation | `spring-boot-starter-validation` | Bean Validation on request DTOs. |
| Security | `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` | Method security, filter chain, Nimbus JOSE JWT decode/encode. |
| Cache | `spring-boot-starter-data-redis` (Lettuce) + Spring Cache abstraction | Cache-aside, distributed locks, pub/sub invalidation. |
| Database | **PostgreSQL 16** | `WITH RECURSIVE`, JSONB, partial + covering indexes. |
| Migrations | **Flyway 10** (`flyway-core` + `flyway-database-postgresql`) | Versioned, repeatable, runs on boot in every environment. |
| Build | **Maven** + Maven Wrapper (`.mvn/` exists; needs `mvnw`, `mvnw.cmd`, wrapper jar/properties) | Reproducible builds in CI. |
| API docs | **springdoc-openapi 2.x** (`springdoc-openapi-starter-webmvc-ui`) | `/swagger-ui.html` on the live URL. |
| Observability | Spring Boot **Actuator** + Micrometer; `logstash-logback-encoder` for JSON logs | `/actuator/health/{liveness,readiness}` for Koyeb health checks. |
| Testing | JUnit 5, **Testcontainers** (`postgresql`, `junit-jupiter`, and a `GenericContainer` for Redis 7), `spring-boot-testcontainers` (`@ServiceConnection`), AssertJ, Awaitility, `spring-security-test` | Real Postgres + real Redis in every integration test. |
| Container | Multi-stage **Dockerfile** — `maven:3.9-eclipse-temurin-21` builder → `eclipse-temurin:21-jre-alpine` runtime, Spring layered jar | Small image, fast Koyeb deploys. |
| Local dev | `docker-compose.yml` — `postgres:16-alpine` (host port **55432**, remapped to avoid clashing with a native Postgres already on 5432 on this machine) + `redis:7-alpine` (6379) | Parity with prod services. **All of Phase 0–6 (T-01…T-35) is built and tested entirely against these containers — no Neon/Upstash account is needed until Phase 7.** |
| CI | **GitHub Actions** — `ci.yml` (`./mvnw verify`, Testcontainers on the runner's Docker) | Every PR and push. |
| CD | **GitHub Actions** — `deploy.yml` → build image → push **GHCR** → `koyeb service redeploy` | Push-to-main deploys. |

### Free-tier services (all zero-cost)

| Service | Provider | Free-tier limits that shape the design |
|---|---|---|
| Postgres | **Neon** | 0.5 GB storage, scale-to-zero after idle, limited connections → pooled endpoint + small Hikari pool. |
| Redis | **Upstash** | ~10k commands/day, TLS-only TCP endpoint → generous TTLs, negative caching, must degrade gracefully. |
| App hosting | **Koyeb** | 1 free web service, 512 MB / 0.1 vCPU → `-XX:MaxRAMPercentage=70`, single instance. |
| Image registry | **GHCR** | Free for public images. |
| CI/CD | **GitHub Actions** | 2000 min/month on free accounts; public repos unlimited. |

---

## 3. Deployment Topology

### 3.0 Development sequencing

Development happens in two stages, deliberately:

1. **Docker-first (Phases 0–6, T-01…T-35).** Everything — app, Postgres, Redis — runs via
   `docker-compose.yml` on the local machine. No Neon or Upstash account is required yet.
   This is also what CI (`ci.yml`) uses: Testcontainers spins up the same `postgres:16-alpine`
   and `redis:7-alpine` images on the runner's own Docker daemon. The app must never hard-code
   assumptions that only hold for the free-tier managed services (e.g. connection pooling
   limits are configured, but the app runs fine against an unconstrained local Postgres too).
2. **Cloud go-live (Phase 7, T-36…T-40).** Only once the full feature set passes locally does
   the project provision Neon + Upstash + Koyeb and wire up GHCR/GitHub Actions deploy. The
   `local` Spring profile always targets docker-compose services; the `prod` profile is the
   only one that talks to the managed free-tier services described below.


```
                     ┌──────────────────────────┐
   git push main ──▶ │   GitHub Actions          │
                     │  ci.yml   → mvnw verify   │  (Testcontainers: pg + redis)
                     │  deploy.yml → docker push │
                     └────────────┬──────────────┘
                                  │ ghcr.io/<user>/gatekeeper:<sha>
                                  ▼
   client ──HTTPS──▶  ┌──────────────────────────┐
                      │  Koyeb web service        │
                      │  gatekeeper (1 instance)  │
                      │  /actuator/health/readiness
                      └───────┬───────────┬───────┘
                       JDBC/TLS│           │rediss://
                              ▼           ▼
                   ┌──────────────┐  ┌──────────────┐
                   │ Neon Postgres│  │Upstash Redis │
                   │  gatekeeper  │  │  cache+lock  │
                   └──────────────┘  └──────────────┘
```

### Neon notes
- One project, one database `gatekeeper`, one role.
- **Use the pooled endpoint** (host contains `-pooler`) and keep
  `spring.datasource.hikari.maximum-pool-size: 5`, `minimum-idle: 1`.
- Scale-to-zero means the first request after idle can take ~1s: set
  `hikari.connection-timeout: 30000` and `initialization-fail-timeout: -1` so boot never
  dies on a cold database.
- Flyway runs on application start (`spring.flyway.enabled=true`), so prod migrations are
  applied by the deploy itself. No separate migration job.
- JDBC URL must carry `?sslmode=require`.

### Upstash notes
- TCP endpoint with TLS: `rediss://default:<password>@<host>:6379`. Lettuce needs
  `spring.data.redis.ssl.enabled: true`.
- Command budget is the binding constraint → **generous, jittered TTLs**, negative caching,
  and no per-request chatter beyond one `GET` on the happy path.
- **Redis is optional at runtime.** Any Redis exception is logged and swallowed; the request
  falls through to Postgres. Redis being down degrades latency, never correctness (§8.6).

### Koyeb notes
- Deploy from GHCR image, port 8080, health check `GET /actuator/health/readiness` (grace 60s).
- Runtime env vars (also the GitHub Actions secret list):

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | Full JDBC URL for the Neon pooled endpoint |
| `DB_USER`, `DB_PASSWORD` | Neon credentials |
| `REDIS_URL` | `rediss://…` Upstash URL |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | PEM RSA keypair for token signing/verification |
| `APP_ENVIRONMENT` | `prod` — consumed by the feature-flag environment-override rule (§7) |
| `SPRING_PROFILES_ACTIVE` | `prod` |

Profiles: `local` (docker-compose, verbose logging, seed data) and `prod` (env-var driven,
JSON logs, `ddl-auto: validate`).

---

## 4. Domain Model

All tenant-owned tables carry a non-null `tenant_id` referencing `tenants(id)`.
Primary keys are `UUID` (`gen_random_uuid()`, `pgcrypto`). Timestamps are `timestamptz`.

| Table | Columns | Constraints |
|---|---|---|
| `tenants` | `id`, `slug`, `name`, `status`, `created_at` | `UNIQUE(slug)`; `status ∈ {ACTIVE, SUSPENDED}` |
| `users` | `id`, `tenant_id`, `email`, `password_hash`, `attributes JSONB`, `enabled`, `created_at` | `UNIQUE(tenant_id, email)` |
| `roles` | `id`, `tenant_id`, `name`, `description`, `created_at` | `UNIQUE(tenant_id, name)` |
| `role_hierarchy` | `parent_role_id`, `child_role_id` | `PK(parent_role_id, child_role_id)`; child inherits parent's permissions |
| `permissions` | `id`, `code`, `description` | `UNIQUE(code)` — global catalogue, e.g. `flag:write` |
| `role_permissions` | `role_id`, `permission_id` | `PK(role_id, permission_id)` |
| `user_roles` | `user_id`, `role_id` | `PK(user_id, role_id)` |
| `feature_flags` | `id`, `tenant_id`, `flag_key`, `enabled`, `rollout_percentage`, `description`, `created_at`, `updated_at`, `version` | `UNIQUE(tenant_id, flag_key)`; `rollout_percentage BETWEEN 0 AND 100`; `version` = JPA `@Version` |
| `flag_whitelist` | `flag_id`, `user_id` | `PK(flag_id, user_id)` |
| `flag_environment_override` | `flag_id`, `environment`, `enabled` | `PK(flag_id, environment)` |
| `refresh_tokens` | `id`, `user_id`, `token_hash`, `expires_at`, `revoked`, `created_at` | `UNIQUE(token_hash)` |
| `audit_log` | `id`, `tenant_id`, `actor_id`, `action`, `target`, `payload JSONB`, `created_at` | append-only |

### 4.1 Index strategy (and rationale)

```sql
-- Tenant-scoped lookups: tenant predicate and lookup key satisfied by ONE index seek.
CREATE UNIQUE INDEX idx_users_tenant_email  ON users(tenant_id, email);
CREATE UNIQUE INDEX idx_roles_tenant_name   ON roles(tenant_id, name);
CREATE UNIQUE INDEX idx_flags_tenant_key    ON feature_flags(tenant_id, flag_key);

-- Hierarchy traversal: the recursive CTE walks child -> parent, so the leading
-- column must be child_role_id. The reverse index supports impact analysis.
CREATE INDEX idx_hierarchy_child  ON role_hierarchy(child_role_id, parent_role_id);
CREATE INDEX idx_hierarchy_parent ON role_hierarchy(parent_role_id, child_role_id);

-- Covering index: permission resolution becomes index-only (no heap fetch).
CREATE INDEX idx_role_perms_covering ON role_permissions(role_id) INCLUDE (permission_id);

-- User's direct roles, index-only.
CREATE INDEX idx_user_roles_covering ON user_roles(user_id) INCLUDE (role_id);

-- Only enabled flags are ever bulk-evaluated -> partial index keeps it tiny.
CREATE INDEX idx_flags_tenant_enabled ON feature_flags(tenant_id) WHERE enabled = true;

-- Audit reads are always "latest first, per tenant".
CREATE INDEX idx_audit_tenant_time ON audit_log(tenant_id, created_at DESC);
```

Rule: **every index on a tenant-owned table leads with `tenant_id`**, because the Hibernate
filter injects `tenant_id = ?` into every query — a non-leading `tenant_id` forces a filter
step after the seek.

---

## 5. Tenant Isolation (discriminator column)

Chosen pattern: **discriminator column** (`tenant_id`) with Hibernate filters.
(Schema-per-tenant rejected — Neon's free connection budget and per-schema Flyway runs make
it impractical here. See §14.)

### 5.1 Mechanism

- `TenantContext` — `ThreadLocal<UUID>` with `set` / `get` / `clear`. **Always cleared in a
  `finally` block** by the filter, or a pooled thread leaks tenancy into the next request.
- `TenantFilter` (a `OncePerRequestFilter` placed **after** the JWT auth filter) reads the
  `tenant` claim from the authenticated token and populates `TenantContext`.
  The `X-Tenant-Slug` header is honoured **only** on `/api/v1/auth/**`, where no token exists
  yet; everywhere else the token is the sole authority.
- `TenantScopedEntity` — a `@MappedSuperclass` carrying `tenant_id`, annotated:

  ```java
  @FilterDef(name = "tenantFilter",
             parameters = @ParamDef(name = "tenantId", type = UUID.class))
  @Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
  ```

- A `TenantAwareEntityManager` aspect / `@Transactional` entry hook enables the filter on the
  current Hibernate `Session`:
  `session.enableFilter("tenantFilter").setParameter("tenantId", TenantContext.require())`.
- Writes: an `@PrePersist` listener stamps `tenant_id` from `TenantContext`; a
  `@PreUpdate` listener throws `CrossTenantAccessException` if an entity's `tenant_id`
  differs from the context.
- Missing tenant context on a tenant-scoped operation is a **hard failure** (500 / explicit
  exception), never a silent unfiltered query.

### 5.2 Known bypass and the rule

Hibernate filters **do not apply to native SQL** — and §6 deliberately uses a native recursive
CTE. Therefore:

> **Rule:** every native query touching a tenant-owned table must contain an explicit
> `tenant_id = :tenantId` predicate, with the value taken from `TenantContext`.

Enforced by (a) a test that greps `@Query(nativeQuery = true)` sources for `tenant_id`, and
(b) the tenant-leakage integration tests in §10.

---

## 6. RBAC / ABAC

### 6.1 Model

- A user has direct roles (`user_roles`).
- A role may inherit from parent roles (`role_hierarchy`): the **child inherits the parent's
  permissions**.
- A user's **effective permissions** = union of the permissions of all roles reachable from
  their direct roles by walking `child → parent` transitively.

### 6.2 The contract query (recursive CTE)

This SQL is the contract. Implementations may not replace it with in-memory graph walking.

```sql
WITH RECURSIVE reachable_roles(role_id, depth) AS (
    -- anchor: the user's directly assigned roles, tenant-scoped
    SELECT ur.role_id, 0
    FROM user_roles ur
    JOIN roles r ON r.id = ur.role_id
    WHERE ur.user_id = :userId
      AND r.tenant_id = :tenantId

    UNION            -- UNION (not UNION ALL): dedupes, so cycles terminate

    -- recursive: every parent of an already-reached role
    SELECT rh.parent_role_id, rr.depth + 1
    FROM role_hierarchy rh
    JOIN reachable_roles rr ON rr.role_id = rh.child_role_id
    WHERE rr.depth < 32              -- hard depth guard against pathological graphs
)
SELECT DISTINCT p.code
FROM reachable_roles rr
JOIN role_permissions rp ON rp.role_id = rr.role_id
JOIN permissions p       ON p.id = rp.permission_id;
```

Cycle safety comes from two independent guards: `UNION` deduplication and the `depth < 32`
bound. Both are asserted by tests (§10).

Writes to `role_hierarchy` additionally run a cycle-detection check (same CTE from the
proposed parent, rejecting the edge if the child is reachable) → `409 Conflict`.

### 6.3 Enforcement — method level

Method security is enabled with `@EnableMethodSecurity` and a **custom expression handler**:

```java
@PreAuthorize("hasPermission('flag', 'write')")
public FeatureFlag update(String key, UpdateFlagRequest req) { … }

@PreAuthorize("hasPermission('role', 'assign') and sameTenant(#roleId)")
public void assignRole(UUID userId, UUID roleId) { … }

@PreAuthorize("hasPermission('report','read') and attr('department') == 'engineering'")
public Report readEngineeringReport() { … }
```

Components:

- `PermissionCatalog` — permission codes are `"<resource>:<action>"` (e.g. `flag:write`,
  `role:assign`, `tenant:admin`, `user:read`).
- `GatekeeperPermissionEvaluator implements PermissionEvaluator` — resolves the caller's
  effective permission set (via the cached loader, §8) and checks membership.
- `GatekeeperMethodSecurityExpressionRoot extends SecurityExpressionRoot` — adds
  `hasPermission(code)`, `sameTenant(id)`, and `attr(name)`.
- `GatekeeperMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler`
  — supplies the custom root.
- Everything is **deny-by-default**: any `/api/**` endpoint not explicitly permitted requires
  authentication, and service methods without an explicit permission are not reachable from
  a controller.

### 6.4 ABAC layer

- `users.attributes` is JSONB, e.g. `{"department":"engineering","region":"eu","level":5}`.
- Attributes are copied into the JWT (`attrs` claim) at login, so evaluation needs no DB hit;
  they are refreshed on token refresh.
- `PolicyEvaluator` evaluates attribute predicates via SpEL against a read-only attribute map.
  Unknown attribute → `null` → predicate false (**deny-by-default**, never fail-open).
- Ordering: RBAC first (does the caller hold the permission?), then ABAC (do their attributes
  satisfy the additional constraint?). Both must pass.

---

## 7. Feature Flag Engine

### 7.1 Evaluation order (first match wins)

1. **Flag not found** → `false` (and negative-cached, §8).
2. **Environment override** for `APP_ENVIRONMENT` exists → its `enabled` value.
3. **User whitelist** contains the caller → `true`.
4. **Global `enabled = false`** → `false`.
5. **Percentage rollout** → `bucket(flagKey, userId) < rollout_percentage`.
6. Otherwise → `true` (enabled, 100% rollout).

The order above is normative. Note in particular that the **whitelist outranks the global
`enabled` switch**: a whitelisted user of a globally disabled flag gets `true`. That is the
intended "let QA in early" behaviour, and it is asserted by a test. The environment override
outranks everything, so `APP_ENVIRONMENT=prod` remains an absolute kill-switch.

### 7.2 Deterministic bucketing

```java
int bucket = Math.floorMod(
        Hashing.murmur3_32_fixed().hashString(flagKey + ":" + userId, UTF_8).asInt(),
        100);
return bucket < rolloutPercentage;
```

Properties, each covered by a test:
- **Stable** — same user + same flag → same result across restarts and instances.
- **Independent** — including `flagKey` in the hash means a user is not always in the first
  bucket for every flag.
- **Uniform** — 10k synthetic users at 30% land within ±3 points of 30%.

### 7.3 Runtime mutation (no restart)

- `PUT /api/v1/flags/{key}` updates the flag under **optimistic locking** (`@Version`);
  a stale write → `409 Conflict`.
- On commit, the flag's cache key is evicted and an invalidation message is published on the
  Redis channel `gatekeeper:invalidate` (§8.4).
- Nothing is read from application startup state — every evaluation reads cache-or-DB, so a
  change is live on the next request.

---

## 8. Caching Strategy (cache-aside + stampede control)

### 8.1 Keys and TTLs

| Key | Value | TTL |
|---|---|---|
| `gk:perm:{tenantId}:{userId}` | `Set<String>` of effective permission codes | 10 min ± up to 60s jitter |
| `gk:flag:{tenantId}:{flagKey}` | serialized flag definition | 5 min ± up to 30s jitter |
| `gk:flag:{tenantId}:{flagKey}` (negative) | tombstone marker | 60s |
| `gk:lock:{key}` | mutex holder token | 5s |

Jitter exists so that keys created together do not expire together and re-stampede.

### 8.2 Read path (cache-aside)

```
GET key
 ├─ hit  → return
 └─ miss → SET lock:{key} <token> NX PX 5000
            ├─ acquired → load from Postgres
            │             SET key <value> PX <ttl+jitter>
            │             DEL lock (only if token matches — no releasing someone else's lock)
            │             return
            └─ not acquired → poll GET key every 50ms, up to 300ms total
                              ├─ value appeared → return it
                              └─ still empty   → load from Postgres directly and return
```

The loser path **never blocks indefinitely**. A slow leader degrades to a few extra DB reads,
which is strictly better than a stalled request. The lock is a stampede *dampener*, not a
correctness mechanism.

### 8.3 Write path / invalidation

Cache eviction happens on **`afterCommit`**, never inside the transaction:

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override public void afterCommit() {
            cache.evict(key);
            publisher.publish("gatekeeper:invalidate", new InvalidationEvent(key));
        }
    });
```

Evicting before commit would let a concurrent reader repopulate the cache from the *old*
committed state, leaving a stale entry with a full TTL ahead of it.

Invalidation triggers:

| Mutation | Keys evicted |
|---|---|
| Role's permissions changed | every `perm:{tenant}:*` for users holding that role (resolved via the reverse CTE) |
| `role_hierarchy` edge added/removed | as above, for all descendant roles |
| User's roles changed | `perm:{tenant}:{userId}` |
| Flag created/updated/deleted, whitelist or override changed | `flag:{tenant}:{flagKey}` |

### 8.4 Multi-instance fan-out

Even though Koyeb's free tier runs one instance, invalidation is published on
`gatekeeper:invalidate` and consumed by a Redis message listener on every instance. This keeps
the design horizontally correct and lets local caches (if added later) be dropped in step.

### 8.5 Negative caching

An unknown flag key is cached as a tombstone for 60s. Without this, a misconfigured client
polling a typo'd key hammers Postgres on every request — the classic miss storm.

### 8.6 Degradation

Every Redis call is wrapped so that `RedisConnectionFailureException` (and any
`RuntimeException` from the client) is logged at `WARN` **once per window** and the call falls
through to Postgres. Consequences, stated explicitly:

- Correctness: unaffected. Postgres is always the source of truth.
- Latency: permission resolution goes from ~1ms to ~10–20ms (Neon cold: worse).
- Load: Postgres sees full read traffic — acceptable at this scale, and the stated SLO impact.

---

## 9. API Surface

Base path `/api/v1`. All endpoints except `/auth/login`, `/auth/refresh`, `/actuator/health/**`,
and the springdoc paths require a bearer token.

### Auth
| Method | Path | Permission | Notes |
|---|---|---|---|
| POST | `/auth/login` | — | Body `{tenantSlug, email, password}` → access + refresh token |
| POST | `/auth/refresh` | — | Rotates the refresh token; old one is revoked |
| POST | `/auth/logout` | authenticated | Revokes the presented refresh token |

### Tenants
| Method | Path | Permission |
|---|---|---|
| POST | `/tenants` | platform bootstrap (see §11) — body `{slug, name, adminEmail, adminPassword}`; provisions the tenant plus its first admin (an `admin` role holding every permission + a user with that role) |
| GET | `/tenants/me` | authenticated |
| PATCH | `/tenants/me` | `tenant:admin` |

### Users
| Method | Path | Permission |
|---|---|---|
| POST | `/users` | `user:write` |
| GET | `/users` / `/users/{id}` | `user:read` |
| PATCH | `/users/{id}/attributes` | `user:write` |
| POST | `/users/{id}/roles` | `role:assign` |
| DELETE | `/users/{id}/roles/{roleId}` | `role:assign` |
| GET | `/users/{id}/permissions` | `user:read` — effective permissions (the CTE) |

### Roles & permissions
| Method | Path | Permission |
|---|---|---|
| POST / GET / PATCH / DELETE | `/roles`, `/roles/{id}` | `role:write` / `role:read` |
| POST | `/roles/{id}/permissions` | `role:write` |
| DELETE | `/roles/{id}/permissions/{code}` | `role:write` |
| POST | `/roles/{id}/parents/{parentId}` | `role:write` — 409 on cycle |
| DELETE | `/roles/{id}/parents/{parentId}` | `role:write` |
| GET | `/permissions` | `role:read` — catalogue |

### Feature flags
| Method | Path | Permission |
|---|---|---|
| POST | `/flags` | `flag:write` |
| GET | `/flags` / `/flags/{key}` | `flag:read` |
| PUT | `/flags/{key}` | `flag:write` — optimistic locking |
| DELETE | `/flags/{key}` | `flag:write` |
| PUT | `/flags/{key}/whitelist/{userId}` / DELETE | `flag:write` |
| PUT | `/flags/{key}/overrides/{environment}` / DELETE | `flag:write` |
| POST | `/flags/{key}/evaluate` | authenticated — evaluates for the caller (or a target user with `flag:read`) |
| GET | `/flags/evaluate` | authenticated — **bulk bootstrap**: all flags evaluated for the caller in one call |

Errors are RFC 7807 `application/problem+json`. `401` unauthenticated, `403` authorized-but-
forbidden, `404` for cross-tenant ids (**never 403** — a 403 would confirm the resource exists
in another tenant).

---

## 10. Testing Strategy

Every integration test extends `AbstractIntegrationTest`, which starts **real Postgres and
real Redis** via Testcontainers with `@ServiceConnection`, reused across the suite
(`withReuse(true)`), and runs Flyway against the fresh container.

Must-pass cases (each is a named task in `session.md`):

| # | Case | Assertion |
|---|---|---|
| T1 | Authentication failure — no token | `401`, no body leakage |
| T2 | Authentication failure — malformed / wrong-signature token | `401` |
| T3 | **Token expiration** | token minted with an `exp` in the past (injected `Clock`) → `401`, and the error distinguishes expiry from malformed |
| T4 | Refresh-token revocation | reusing a rotated refresh token → `401` and the whole chain is revoked |
| T5 | Authorization failure | authenticated user lacking `flag:write` → `403` |
| T6 | **Tenant leakage — API** | tenant A's token requesting tenant B's role/flag/user by id → `404` for every such endpoint |
| T7 | **Tenant leakage — repository** | with `TenantContext` = A, `findAll()` on each tenant-scoped repository returns zero of B's rows |
| T8 | **Tenant leakage — native CTE** | the permission CTE run as A's user never returns permissions granted only in B |
| T9 | Tenant leakage — write | persisting an entity stamped with B's id while context is A → `CrossTenantAccessException` |
| T10 | Role hierarchy correctness | 3-level chain grants the root's permissions to the leaf |
| T11 | Role hierarchy cycle | an introduced cycle terminates and returns the correct set; the API rejects the cycle-creating edge with `409` |
| T12 | Cache invalidation | grant a permission → the next call reflects it immediately (no TTL wait) |
| T13 | **Cache stampede** | 50 concurrent threads on a cold key → the DB loader is invoked ≤ 2 times (counting spy) and all 50 get the correct value |
| T14 | Redis down | stop the Redis container mid-test → requests still succeed from Postgres |
| T15 | Flag evaluation order | environment override > whitelist > global disable > percentage, each asserted independently |
| T16 | Rollout determinism | same user+flag → identical result across 100 evaluations and a context restart |
| T17 | Rollout distribution | 10k users at 30% → 27–33% enabled |
| T18 | Optimistic locking | concurrent flag updates → one `409` |
| T19 | Index usage | `EXPLAIN ANALYZE` on the permission CTE shows index scans (no `Seq Scan` on `role_permissions` / `user_roles`) |

Unit tests cover the bucketing hash, the policy/SpEL evaluator, and the flag rule ordering
without any container.

---

## 11. Bootstrap & Seed Data

- Flyway `V6__seed_permissions.sql` keeps the `permissions` catalogue populated. Versioned, not
  repeatable — Flyway always runs repeatable (`R__`) migrations after every versioned one
  regardless of version number, which would put this after `V900__seed_demo.sql` and leave the
  demo seed unable to find any permission rows to grant (a real bug hit during T-35).
- `V900__seed_demo.sql` creates a demo tenant `acme`, roles `admin → editor → viewer`
  (inheritance chain), a demo admin user, and two flags (one at 50% rollout, one whitelisted).
  It lives under `db/seed/`, which is **not** on the `local` profile's Flyway path — `local` is
  what integration tests activate, and a persistent seed (Flyway only ever applies a versioned
  migration once against the docker-compose volume) would collide with tests that reset their
  own tables broadly. Instead it's an opt-in `seed` profile layered on top:
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,seed` (also a real fix from T-35).
- Tenant creation in prod is a bootstrap-only operation: `POST /tenants` requires a
  `X-Bootstrap-Token` matching an env var, and is the single non-tenant-scoped write path.
  The same call provisions the tenant's first principal — an `admin` role holding every
  catalogue permission and a user (`adminEmail`/`adminPassword` from the request body)
  assigned to it — since otherwise a freshly bootstrapped tenant would have no one able to
  authenticate or call `user:write`. `TenantContext` is set to the new tenant for the
  duration of that provisioning so the `@PrePersist` listener stamps `tenant_id`.

---

## 12. Definition of Done (live checklist)

- [ ] Public Koyeb URL returns `200` on `/actuator/health`.
- [ ] `/swagger-ui.html` reachable on the live URL.
- [ ] Neon holds the full Flyway schema; `flyway_schema_history` is clean.
- [ ] Upstash shows cache traffic under load; killing it does not break the API.
- [ ] Demo tenant + admin exist; the README curl walkthrough works end-to-end against the
      live URL (login → create role → grant permission → create flag → evaluate).
- [ ] `ci.yml` green on `main`; `deploy.yml` has deployed at least one tagged image.
- [ ] All §10 tests pass in CI, including T3, T6, T7, T13.
      _(Locally: 62 green against docker-compose + 1 `@Disabled` perf harness; the 2 remaining —
      `TenantUserPersistenceIT`, `AbstractIntegrationTestSmokeTest` — are Testcontainers-based and
      blocked only by this machine's Docker-socket sandboxing, per the T-05 note. CI runs them on
      GitHub's daemon.)_
- [x] `EXPLAIN ANALYZE` output for the permission CTE recorded in §13. _(§13.1–13.5, incl. real
      cache miss/hit endpoint timings.)_

---

## 13. Performance Appendix

Measured against a seeded dataset of 50 tenants × 200 users × 20 roles (10,000 users, 1,000
roles) via `docker-compose` Postgres. Every user was assigned the deepest role in a 20-level
inheritance chain, forcing the CTE to walk the full chain on every run. `role_permissions` was
populated with all 8 catalogue permissions on every role (8,000 rows) specifically to make the
covering index's benefit measurable — the seed script lives at
`scratchpad/seed_perf.sql` in the working session; it is not part of the shipped migrations.

### 13.1 CTE, indexes dropped (`idx_role_perms_covering`, `idx_user_roles_covering` removed)

Both `user_roles` and `role_permissions` fall back to `user_roles_pkey` / a full `Seq Scan`.
Execution time: **3.53 ms**, `Planning Buffers: shared hit=293 dirtied=1`.

### 13.2 CTE, indexes present, planner's own choice

With the composite indexes restored, `Postgres's own cost-based planner still chose `Seq Scan`
for `role_permissions` (8,000 rows) and `role_hierarchy` (950 rows) over the covering/composite
indexes — a single hash join over a table that size was estimated cheaper than 20 separate
index probes (one per role in the reachable set). This is the query planner behaving
*correctly* for this table size, not a defect: **Execution time: 4.50 ms** (marginally worse
than 13.1, dominated by scanning the now-larger 8,000-row `role_permissions` table once).

### 13.3 CTE, indexes forced on (`SET enable_seqscan = off`)

Forcing the planner to use the composite/covering indexes changes the plan for both recursive
steps and the final join:

- The hierarchy walk switches from `Seq Scan on role_hierarchy` (950 rows × 20 loops) to
  `Index Only Scan using idx_hierarchy_child` (20 loops, each an index probe).
- The permission join switches from `Seq Scan on role_permissions` (8,000 rows) to
  `Bitmap Index Scan on idx_role_perms_covering` + `Bitmap Heap Scan` (160 rows total).

**Execution time: 1.55 ms** — **~3× faster** than the planner's own default choice (13.2) and
**~2.3× faster** than the no-index baseline (13.1), despite the planner's row-count estimates
saying otherwise.

**Interpretation:** at this seed's scale (single-digit thousands of rows per tenant-scoped
table), Postgres's cost model slightly *undervalues* the indexes because it doesn't fully
account for the CTE's 20-iteration loop cost per index probe. The composite/covering indexes
are still measurably faster in wall-clock terms even here, and the gap will only widen as
`role_permissions` and `role_hierarchy` grow with more tenants — exactly the regime `ANALYZE`
and Postgres's statistics tune themselves for in production. This is why §4.1's indexes are
kept even though a synthetic small-scale `EXPLAIN` alone might look unconvincing: the honest
finding is "the planner's default plan is close, but forcing the index is still faster," not
"the index makes an dramatic order-of-magnitude difference on this seed size."

### 13.4 Index-only scans already active without forcing

Independent of the above, `user_roles_pkey` and `roles_pkey` were used as `Index Only Scan` /
`Index Scan` in every plan variant (13.1–13.3) for the anchor step of the recursive CTE
(`WHERE ur.user_id = :userId`) — these composite lookups were never the bottleneck; the
row-count-dependent choice was specific to `role_permissions` and `role_hierarchy`.

### 13.5 Cache hit/miss (T-22)

Measured end-to-end through `GET /users/{id}/permissions` (real HTTP loopback, JWT decode,
method security, JSON serialization) against docker-compose Postgres + Redis, 200 samples
after 20 warm-up iterations, via the `LocalPermissionEndpointTimingIT` harness (`@Disabled`;
run explicitly with `-Djunit.jupiter.conditions.deactivate=…DisabledCondition`). The user
under test is the bootstrapped tenant admin, holding all 8 catalogue permissions through a
single `admin` role — a real but shallow reachable set.

| Scenario | Time |
|---|---|
| CTE, no composite indexes | 3.53 ms |
| CTE, with composite indexes (planner's own choice: Seq Scan, table too small to prefer index) | 4.50 ms |
| CTE, composite indexes forced on (`enable_seqscan=off`) | **1.55 ms** |
| `/users/{id}/permissions` cache miss (CTE + Redis populate) | p50 **7.5 ms**, p95 13.2 ms |
| `/users/{id}/permissions` cache hit (single Redis GET + deserialize) | p50 **4.9 ms**, p95 8.7 ms |

**Interpretation:** the cache saves ~2.6 ms p50 (~35%) per call at this scale. Most of the
absolute number on both rows is fixed request overhead (Tomcat, the resource-server JWT
decode, Jackson) rather than the permission lookup itself — the hit path still touches Redis
over TCP. The saved delta is the CTE execution plus the `SET` write, and it widens with the
size of the user's reachable role graph (the miss path scales with hierarchy depth × grants;
the hit path is constant). The wider p95 on the miss path (13.2 vs 8.7 ms) is the extra
variance of a DB round-trip and query planning under local JVM/pool jitter; against a
scale-to-zero Neon endpoint the miss-path tail would be far worse (cold connection ~1 s),
which is the real production argument for the cache — it removes that tail entirely on hits.

---

## 14. Decision Log

| Decision | Chosen | Rejected, and why |
|---|---|---|
| Tenant isolation | **Discriminator column** + Hibernate `@Filter` | *Schema-per-tenant*: Neon's free connection budget plus per-schema Flyway runs and connection-provider switching is disproportionate here. *RLS*: strong, but requires setting a session variable on every pooled connection — kept as a documented future hardening step. |
| Authentication | **Self-issued JWT** (RSA, access + rotating refresh) | *External OIDC (Keycloak/Auth0)*: another service to keep alive on free tiers, and it hides the token-expiry/auth-failure behaviour this project exists to demonstrate. *API keys*: deferred; can be added as a second `AuthenticationProvider`. |
| Hosting | **Neon + Upstash + Koyeb + GHCR + GitHub Actions** | *Render*: free instances sleep after 15 min with ~50s cold starts. *Fly.io / Supabase*: Supabase pauses after a week of inactivity; Fly needs more per-app config. |
| Permission resolution | **Recursive CTE in Postgres** | *In-memory graph walk*: would need the whole role graph loaded and cached per tenant, and hides the indexing work that is a core goal. |
| Stampede control | **Redis `SET NX` mutex with bounded wait + DB fallback** | *Blocking lock*: turns a cache miss into a latency cliff. *Probabilistic early expiry*: elegant but harder to assert in a test. |
| Flag bucketing | **murmur3_32 over `flagKey:userId`** | *`userId.hashCode() % 100`*: not stable across JVMs and correlates users across flags. |
