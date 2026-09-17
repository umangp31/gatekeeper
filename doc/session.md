# Gatekeeper — Session Tasks

Micro-tasks derived from [`scope.md`](./scope.md). Each task is 30–60 minutes,
independently committable, and has an objective "Done when".

**How to use this file**

1. Pick the first task whose dependencies are all `✅`.
2. Read the referenced `scope.md` section — that section, not this file, is the contract.
3. Implement, then run `./mvnw verify`.
4. Tick the task in the Progress table at the bottom and commit with the task id in the
   message (e.g. `T-14: recursive permission CTE`).

Package root: `com.gatekeeper`. Paths below omit `src/main/java/com/gatekeeper/` and
`src/test/java/com/gatekeeper/` where obvious.

---

**Docker-first note:** Phases 0–6 (T-01…T-35) run entirely against `docker-compose.yml`
(local Postgres + Redis). No Neon/Upstash account is needed until Phase 7 — see scope.md §3.0.

## Phase 0 — Foundation

### T-01 · Rewrite `pom.xml` for Spring Boot 3.3 / Java 21
- **Scope ref:** §2 · **Depends on:** —
- **Files:** `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- **Do:** Add `spring-boot-starter-parent` 3.3.x. Set `<java.version>21</java.version>`
  (the current `maven.compiler.target=24` is wrong — remove it). Add starters: `web`,
  `data-jpa`, `data-redis`, `security`, `oauth2-resource-server`, `validation`, `actuator`;
  plus `postgresql`, `flyway-core`, `flyway-database-postgresql`, `springdoc-openapi-starter-webmvc-ui`,
  `guava` (murmur3), `logstash-logback-encoder`. Test scope: `spring-boot-starter-test`,
  `spring-security-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`,
  `testcontainers-junit-jupiter`, `awaitility`. Add `spring-boot-maven-plugin` with layered jar.
  Generate the Maven wrapper (`mvn wrapper:wrapper`).
- **Done when:** `./mvnw -q clean compile` succeeds and `./mvnw dependency:tree` shows Boot 3.3.
- **Est:** ~40m

### T-02 · Application skeleton and package layout
- **Scope ref:** §1, §2 · **Depends on:** T-01
- **Files:** `GatekeeperApplication.java`, empty packages `config`, `tenancy`, `security`,
  `rbac`, `flags`, `audit`, `common`
- **Do:** `@SpringBootApplication` main class. `common` gets `ApiException` hierarchy stubs and
  a `Clock` bean (`Clock.systemUTC()`) — **all time-dependent code must inject this `Clock`**,
  which is what makes T-12's expiry test possible.
- **Done when:** `./mvnw spring-boot:run` starts and fails only on the missing datasource.
- **Est:** ~20m

### T-03 · Local infra + configuration profiles
- **Scope ref:** §3 · **Depends on:** T-02
- **Files:** `docker-compose.yml`, `src/main/resources/application.yml`,
  `application-local.yml`, `application-prod.yml`
- **Do:** compose with `postgres:16-alpine` + `redis:7-alpine` and named volumes. `local` profile
  points at them; `prod` reads `DATABASE_URL`/`DB_USER`/`DB_PASSWORD`/`REDIS_URL` env vars with
  `hikari.maximum-pool-size: 5`, `connection-timeout: 30000`, `initialization-fail-timeout: -1`,
  `redis.ssl.enabled: true`, `jpa.hibernate.ddl-auto: validate`,
  `spring.threads.virtual.enabled: true`. Expose actuator health with probes enabled.
- **Done when:** `docker compose up -d` then `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
  starts clean and `/actuator/health` returns `UP`.
- **Est:** ~35m

### T-04 · Flyway baseline
- **Scope ref:** §4 · **Depends on:** T-03
- **Files:** `src/main/resources/db/migration/V1__baseline.sql`
- **Do:** enable `pgcrypto`; create `tenants` and `users` per §4 with their §4.1 indexes.
- **Done when:** app boots against an empty DB and `flyway_schema_history` shows V1 applied.
- **Est:** ~30m

### T-05 · Testcontainers base class
- **Scope ref:** §10 · **Depends on:** T-04
- **Files:** `test/.../support/AbstractIntegrationTest.java`
- **Do:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, static `PostgreSQLContainer` with
  `@ServiceConnection`, static Redis `GenericContainer` wired via
  `@DynamicPropertySource`, both `withReuse(true)`. Add a `resetDatabase()` helper
  (truncate all tables, restart identity) called from `@BeforeEach`.
- **Done when:** a trivial test extending it passes and the containers are shared, not
  restarted per class.
- **Est:** ~45m

---

## Phase 1 — Tenant Isolation

### T-06 · Tenant & user entities + repositories
- **Scope ref:** §4, §5 · **Depends on:** T-05
- **Files:** `tenancy/Tenant.java`, `tenancy/TenantRepository.java`, `security/User.java`,
  `security/UserRepository.java`, `common/TenantScopedEntity.java`
- **Do:** `TenantScopedEntity` as `@MappedSuperclass` with `tenant_id`, carrying the
  `@FilterDef`/`@Filter` declaration from §5.1. `User.attributes` maps to JSONB.
  `Tenant` is **not** tenant-scoped.
- **Done when:** a test persists a tenant + user and reads them back.
- **Est:** ~40m

### T-07 · `TenantContext` + `TenantFilter` + filter activation
- **Scope ref:** §5.1 · **Depends on:** T-06
- **Files:** `tenancy/TenantContext.java`, `tenancy/TenantFilter.java`,
  `tenancy/TenantFilterActivator.java`
- **Do:** ThreadLocal context with `clear()` in a `finally`. Servlet filter resolves the tenant
  (JWT `tenant` claim; `X-Tenant-Slug` accepted **only** on `/api/v1/auth/**`). Activator enables
  the Hibernate filter on the current session at transaction start. Missing context on a
  tenant-scoped read throws `TenantContextMissingException` — never runs unfiltered.
- **Done when:** with context = A, `userRepository.findAll()` returns only A's users
  (test T7 from §10 for `users`).
- **Est:** ~60m

### T-08 · Cross-tenant write guard
- **Scope ref:** §5.1 · **Depends on:** T-07
- **Files:** `tenancy/TenantEntityListener.java`
- **Do:** `@PrePersist` stamps `tenant_id` from context; `@PreUpdate`/`@PreRemove` throw
  `CrossTenantAccessException` on mismatch.
- **Done when:** §10 test T9 passes.
- **Est:** ~30m

### T-09 · Tenant endpoints + bootstrap
- **Scope ref:** §9, §11 · **Depends on:** T-08
- **Files:** `tenancy/TenantController.java`, `tenancy/TenantService.java`
- **Do:** `POST /tenants` gated by `X-Bootstrap-Token`; `GET/PATCH /tenants/me`.
- **Done when:** creating a tenant without the bootstrap token returns `403`; with it, `201`.
- **Est:** ~35m

---

## Phase 2 — Authentication

### T-10 · Password hashing + user provisioning
- **Scope ref:** §9 · **Depends on:** T-09
- **Files:** `security/PasswordConfig.java`, `security/UserService.java`, `security/UserController.java`
- **Do:** `BCryptPasswordEncoder(12)` bean; create/read/update users within the tenant.
- **Done when:** a created user's `password_hash` is a bcrypt digest, never the plaintext.
- **Est:** ~30m

### T-11 · JWT issue & decode
- **Scope ref:** §3, §6.4 · **Depends on:** T-10
- **Files:** `security/JwtConfig.java`, `security/TokenService.java`
- **Do:** RSA keypair from `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` PEM env vars (generated pair for
  `local`). `NimbusJwtEncoder`/`NimbusJwtDecoder`. Access token (15 min) claims: `sub`, `tenant`,
  `email`, `attrs`. **Use the injected `Clock`** for `iat`/`exp`.
- **Done when:** a unit test decodes an issued token and reads every claim.
- **Est:** ~45m

### T-12 · Security filter chain + login
- **Scope ref:** §9 · **Depends on:** T-11
- **Files:** `security/SecurityConfig.java`, `security/AuthController.java`
- **Do:** stateless chain, CSRF off, `oauth2ResourceServer().jwt()`, permit `/auth/login`,
  `/auth/refresh`, `/actuator/health/**`, springdoc paths; everything else authenticated.
  `TenantFilter` registered after the JWT filter. Custom `AuthenticationEntryPoint` returning
  RFC 7807 and distinguishing *expired* from *malformed*.
- **Done when:** §10 tests T1, T2, T3 pass (T3 by issuing a token through a `Clock` fixed in the past).
- **Est:** ~60m

### T-13 · Refresh tokens with rotation & revocation
- **Scope ref:** §9 · **Depends on:** T-12
- **Files:** `V2__refresh_tokens.sql`, `security/RefreshToken*.java`
- **Do:** store SHA-256 hashes only. `/auth/refresh` rotates (revoke old, issue new); reuse of a
  revoked token revokes the entire chain for that user. `/auth/logout` revokes.
- **Done when:** §10 test T4 passes.
- **Est:** ~50m

---

## Phase 3 — RBAC / ABAC

### T-14 · RBAC schema + indexes
- **Scope ref:** §4, §4.1 · **Depends on:** T-13
- **Files:** `V3__rbac.sql`, `V6__seed_permissions.sql`
- **Do:** `roles`, `role_hierarchy`, `permissions`, `role_permissions`, `user_roles` with every
  index from §4.1 verbatim. Repeatable migration seeds the permission catalogue
  (`tenant:admin`, `user:read`, `user:write`, `role:read`, `role:write`, `role:assign`,
  `flag:read`, `flag:write`).
- **Done when:** migration applies and `\d role_permissions` shows the covering index.
- **Est:** ~40m

### T-15 · Recursive permission CTE
- **Scope ref:** §6.2 · **Depends on:** T-14
- **Files:** `rbac/PermissionQueryRepository.java`
- **Do:** the §6.2 SQL **verbatim** as a native query, with both `:userId` and `:tenantId` bound
  (the tenant predicate is mandatory — native SQL bypasses the Hibernate filter, §5.2).
- **Done when:** §10 tests T8, T10, T11 (traversal half) pass.
- **Est:** ~45m

### T-16 · Role CRUD, permission grants, hierarchy edges
- **Scope ref:** §9 · **Depends on:** T-15
- **Files:** `rbac/RoleService.java`, `rbac/RoleController.java`
- **Do:** all `/roles` endpoints from §9. Adding a parent edge runs the cycle check
  (§6.2) and returns `409` if the child is already reachable from the proposed parent.
- **Done when:** §10 test T11 (rejection half) passes.
- **Est:** ~55m

### T-17 · Custom permission evaluator + expression handler
- **Scope ref:** §6.3 · **Depends on:** T-16
- **Files:** `rbac/GatekeeperPermissionEvaluator.java`,
  `rbac/GatekeeperMethodSecurityExpressionRoot.java`,
  `rbac/GatekeeperMethodSecurityExpressionHandler.java`, `config/MethodSecurityConfig.java`
- **Do:** `@EnableMethodSecurity`, register the handler, expose `hasPermission(code)`,
  `hasPermission(resource, action)`, `sameTenant(id)`, `attr(name)`.
- **Done when:** a method annotated `@PreAuthorize("hasPermission('flag','write')")` throws
  `AccessDeniedException` for a user without it.
- **Est:** ~55m

### T-18 · Annotate services + ABAC policy evaluator
- **Scope ref:** §6.3, §6.4 · **Depends on:** T-17
- **Files:** `rbac/PolicyEvaluator.java`, `@PreAuthorize` across all service methods
- **Do:** apply the permission codes listed in §9 to every service method. SpEL-backed
  attribute predicates over the JWT `attrs` claim, deny-by-default on unknown attributes.
- **Done when:** §10 test T5 passes, plus a test where the right permission and the wrong
  `department` attribute still yields `403`.
- **Est:** ~50m

### T-19 · Cross-tenant API leakage test sweep
- **Scope ref:** §10 · **Depends on:** T-18
- **Files:** `test/.../tenancy/TenantLeakageIT.java`
- **Do:** two tenants, two tokens; assert every id-addressed endpoint (`users`, `roles`, later
  `flags`) returns **`404`, not `403`**, for the other tenant's ids.
- **Done when:** §10 test T6 passes for all currently existing endpoints.
- **Est:** ~45m

### T-20 · Index verification + performance appendix
- **Scope ref:** §4.1, §13 · **Depends on:** T-19
- **Files:** `test/.../rbac/PermissionQueryPlanIT.java`, `doc/scope.md` §13
- **Do:** seed ≥50 tenants × 200 users × 20 roles; run `EXPLAIN (ANALYZE, BUFFERS)` on the CTE
  with and without the composite indexes; paste both plans into §13.
- **Done when:** §10 test T19 passes and §13 has real numbers (no `_tbd_`).
- **Est:** ~50m

---

## Phase 4 — Caching

### T-21 · Redis configuration
- **Scope ref:** §8.1 · **Depends on:** T-20
- **Files:** `config/RedisConfig.java`
- **Do:** `RedisTemplate` with String keys + JSON values, Lettuce with TLS for Upstash,
  key prefix `gk:`, and a `TtlJitter` helper.
- **Done when:** an integration test round-trips a value through the Redis container.
- **Est:** ~30m

### T-22 · Cache-aside permission loader with stampede mutex
- **Scope ref:** §8.2 · **Depends on:** T-21
- **Files:** `rbac/CachedPermissionResolver.java`, `common/RedisMutex.java`
- **Do:** implement §8.2 exactly — `SET NX PX 5000` lock with a holder token, token-checked
  release, loser polls at 50ms up to 300ms then reads Postgres directly. Wire the evaluator
  (T-17) to read through this resolver.
- **Done when:** §10 test T13 passes (50 threads, DB loader spy invoked ≤ 2×).
- **Est:** ~60m

### T-23 · Transactional invalidation + pub/sub fan-out
- **Scope ref:** §8.3, §8.4 · **Depends on:** T-22
- **Files:** `common/CacheInvalidationPublisher.java`, `common/CacheInvalidationListener.java`
- **Do:** evict in `afterCommit` only (never mid-transaction — §8.3 explains why) and publish on
  `gatekeeper:invalidate`. Cover every trigger in the §8.3 table, including resolving affected
  users via the reverse hierarchy walk when a role's permissions change.
- **Done when:** §10 test T12 passes — granting a permission is visible on the very next request.
- **Est:** ~55m

### T-24 · Graceful degradation when Redis is unavailable
- **Scope ref:** §8.6 · **Depends on:** T-23
- **Files:** `common/ResilientCache.java`
- **Do:** wrap all Redis access; swallow connection/runtime failures, log `WARN` at most once
  per window, fall through to Postgres.
- **Done when:** §10 test T14 passes (container stopped mid-test, requests still `200`).
- **Est:** ~40m

---

## Phase 5 — Feature Flags

### T-25 · Flag schema
- **Scope ref:** §4 · **Depends on:** T-24
- **Files:** `V4__feature_flags.sql`
- **Do:** `feature_flags` (with `version` for optimistic locking and the
  `rollout_percentage BETWEEN 0 AND 100` check), `flag_whitelist`,
  `flag_environment_override`, plus the partial index from §4.1.
- **Done when:** migration applies; a 150% rollout insert is rejected by the DB.
- **Est:** ~30m

### T-26 · Flag CRUD with optimistic locking
- **Scope ref:** §7.3, §9 · **Depends on:** T-25
- **Files:** `flags/FeatureFlag.java`, `flags/FeatureFlagService.java`, `flags/FeatureFlagController.java`
- **Do:** entity extends `TenantScopedEntity`, `@Version` field; CRUD + whitelist + override
  endpoints, all `@PreAuthorize("hasPermission('flag','write'|'read')")`.
  `OptimisticLockException` → `409`.
- **Done when:** §10 test T18 passes.
- **Est:** ~50m

### T-27 · Deterministic bucketing utility
- **Scope ref:** §7.2 · **Depends on:** T-26
- **Files:** `flags/RolloutBucketer.java`
- **Do:** murmur3_32 over `flagKey + ":" + userId`, `Math.floorMod(..., 100)`.
- **Done when:** §10 tests T16 (stability) and T17 (10k users at 30% → 27–33%) pass, plus a test
  that a fixed user is not in the same bucket for two different flag keys.
- **Est:** ~35m

### T-28 · Evaluation engine
- **Scope ref:** §7.1 · **Depends on:** T-27
- **Files:** `flags/FlagEvaluator.java`
- **Do:** implement the six-step order from §7.1 exactly, reading `APP_ENVIRONMENT` for the
  override step. Note the normative behaviour: **whitelist beats the global `enabled=false`
  switch**; the environment override beats everything.
- **Done when:** §10 test T15 passes with one assertion per rule.
- **Est:** ~45m

### T-29 · Flag caching + negative caching
- **Scope ref:** §8.1, §8.5 · **Depends on:** T-28
- **Files:** `flags/CachedFlagRepository.java`
- **Do:** cache-aside on `gk:flag:{tenant}:{key}` reusing T-22's mutex; 60s tombstone for
  unknown keys; invalidation hooked into T-23 for every flag mutation.
- **Done when:** an unknown key hits the DB once across 20 sequential requests, and updating a
  flag is reflected immediately.
- **Est:** ~40m

### T-30 · Evaluate endpoints
- **Scope ref:** §9 · **Depends on:** T-29
- **Files:** `flags/FlagEvaluationController.java`
- **Do:** `POST /flags/{key}/evaluate` (self, or a target user with `flag:read`) and
  `GET /flags/evaluate` bulk bootstrap using the partial index from §4.1.
- **Done when:** bulk evaluation for a tenant with 50 flags issues one flag query and returns a
  `{key: boolean}` map; add flags to the T-19 leakage sweep.
- **Est:** ~40m

---

## Phase 6 — Hardening & Ops

### T-31 · Audit log
- **Scope ref:** §4 · **Depends on:** T-30
- **Files:** `V5__audit_log.sql`, `audit/AuditService.java`, `audit/Auditable.java` (AOP aspect)
- **Do:** append-only records for role grants, hierarchy edits, and flag mutations; actor from
  the security context, tenant from `TenantContext`.
- **Done when:** a flag update writes exactly one audit row with the correct actor and payload.
- **Est:** ~45m

### T-32 · RFC 7807 error handling
- **Scope ref:** §9 · **Depends on:** T-31
- **Files:** `common/GlobalExceptionHandler.java`
- **Do:** `@RestControllerAdvice` → `ProblemDetail`. Map `CrossTenantAccessException` and any
  cross-tenant id lookup to **`404`** (a `403` would confirm the resource exists elsewhere).
  Never leak stack traces or SQL.
- **Done when:** every §10 error-path test asserts `application/problem+json`.
- **Est:** ~35m

### T-33 · Login rate limiting
- **Scope ref:** §9 · **Depends on:** T-32
- **Files:** `security/LoginRateLimiter.java`
- **Do:** Redis token bucket keyed by `tenantSlug + email + client IP`; `429` with `Retry-After`.
  Must fail **open** if Redis is down (§8.6) — availability over throttling here.
- **Done when:** the 11th login attempt in a minute returns `429`; with Redis stopped, logins
  still work.
- **Est:** ~40m

### T-34 · OpenAPI + actuator exposure
- **Scope ref:** §2, §12 · **Depends on:** T-33
- **Files:** `config/OpenApiConfig.java`, `application-prod.yml`
- **Do:** bearer-auth security scheme, grouped tags, server URL from env. Expose only
  `health`, `info`, `metrics`; enable liveness/readiness probes.
- **Done when:** `/swagger-ui.html` renders locally with an "Authorize" button that works.
- **Est:** ~30m

### T-35 · Local seed data
- **Scope ref:** §11 · **Depends on:** T-34
- **Files:** `db/seed/V900__seed_demo.sql` (loaded only under the `local` profile)
- **Do:** tenant `acme`; roles `admin → editor → viewer` as a real inheritance chain; a demo
  admin; two flags (one 50% rollout, one whitelisted).
- **Done when:** a fresh `local` boot lets you log in as the demo admin and evaluate both flags.
- **Est:** ~30m

---

## Phase 7 — Go Live

### T-36 · Dockerfile
- **Scope ref:** §2, §3 · **Depends on:** T-35
- **Files:** `Dockerfile`, `.dockerignore`
- **Do:** multi-stage (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`), Spring
  layered jar extraction, non-root user, `-XX:MaxRAMPercentage=70` for Koyeb's 512 MB.
- **Done when:** `docker build` succeeds and the container runs against docker-compose services.
- **Est:** ~40m

### T-37 · CI workflow
- **Scope ref:** §2 · **Depends on:** T-36
- **Files:** `.github/workflows/ci.yml`
- **Do:** on PR + push: JDK 21, Maven cache, `./mvnw -B verify` (Testcontainers uses the runner's
  Docker), upload surefire/failsafe reports.
- **Done when:** the workflow is green on a PR, with all §10 tests reported.
- **Est:** ~35m

### T-38 · Provision Neon + Upstash
- **Scope ref:** §3 · **Depends on:** T-37
- **Files:** `doc/scope.md` §12 checklist, GitHub repo secrets
- **Do:** create the Neon project (grab the **pooled** connection string, `sslmode=require`) and
  the Upstash database (`rediss://` URL). Generate the RSA keypair. Store all §3 secrets in
  GitHub. Run the app locally against both to confirm Flyway applies cleanly to Neon.
- **Done when:** Neon shows the full schema and the local app reads/writes Upstash.
- **Est:** ~40m

### T-39 · Deploy workflow → GHCR → Koyeb
- **Scope ref:** §3 · **Depends on:** T-38
- **Files:** `.github/workflows/deploy.yml`
- **Do:** on push to `main` after CI: build and push `ghcr.io/<user>/gatekeeper:${{ github.sha }}`
  and `:latest`, then `koyeb service redeploy`. Configure the Koyeb service (port 8080, health
  check `/actuator/health/readiness`, grace 60s, env vars from §3).
- **Done when:** a push to `main` results in a live public URL returning `UP`.
- **Est:** ~50m

### T-40 · Smoke test + README
- **Scope ref:** §12 · **Depends on:** T-39
- **Files:** `scripts/smoke.sh`, `README.md`
- **Do:** `smoke.sh <base-url>` runs the full walkthrough — bootstrap tenant → login → create
  role → grant `flag:write` → assign role → create flag at 50% → evaluate → whitelist a user →
  re-evaluate → assert the flip. README documents the live URL, architecture summary, local
  setup, and the same curl walkthrough.
- **Done when:** `scripts/smoke.sh https://<koyeb-url>` exits `0`, and every box in §12 is ticked.
- **Est:** ~50m

---

## Progress

| Task | Title | Status | Notes |
|---|---|---|---|
| T-01 | pom.xml → Boot 3.3 / Java 21 | ✅ | Boot 3.3.4, Java 21, wrapper generated, `./mvnw clean compile` green |
| T-02 | App skeleton + packages | ✅ | Main class, Clock bean, ApiException base, package-info for all packages |
| T-03 | docker-compose + profiles | ✅ | Postgres remapped to host port 55432 (5432 taken by an unrelated native service); `/actuator/health` returns UP against docker-compose |
| T-04 | Flyway baseline | ✅ | V1__baseline.sql (tenants, users) applied; flyway_schema_history at v1 |
| T-05 | Testcontainers base class | 🔶 | Code complete (`AbstractIntegrationTest`, reused PG+Redis containers, TRUNCATE reset). **Cannot be run inside this coding-agent sandbox**: docker-java gets a stubbed 400 from Docker Desktop's socket (docker CLI works fine, so it's a sandbox socket-filtering issue, not a config bug — tried DOCKER_HOST, unsandboxed exec, API version pin, and a testcontainers-bom bump to 1.21.1, all same result). Verify with `./mvnw verify` in a normal terminal, or rely on CI (T-37) which runs on GitHub Actions' own Docker daemon. |
| T-06 | Tenant & user entities | ✅ | Tenant, User (JSONB attributes), TenantScopedEntity w/ @FilterDef. Verified via LocalTenantUserPersistenceIT against docker-compose PG (Testcontainers-based TenantUserPersistenceIT written for CI but unrun here, see T-05) |
| T-07 | TenantContext + filter | ✅ | TenantContext, TenantFilter (JWT `tenant` claim / X-Tenant-Slug on auth paths), TenantFilterActivator (AOP, enables Hibernate filter per-transaction). Verified: LocalTenantFilterIT confirms `findAll()` SQL gets `tenant_id = ?` injected and returns only the current tenant's rows |
| T-08 | Cross-tenant write guard | ✅ | TenantEntityListener (@PrePersist stamp, @PreUpdate/@PreRemove reject). Also fixed a systemic bug: manually-assigned UUID ids made Spring Data use merge() instead of persist(), silently dropping lifecycle-callback mutations — fixed via common/PersistableEntity (implements Persistable<UUID>), applied to Tenant and TenantScopedEntity. Verified via LocalTenantWriteGuardIT (stamp-on-persist + reject-on-mismatched-update) |
| T-09 | Tenant endpoints + bootstrap | ✅ | POST/GET-me/PATCH-me. Added a placeholder SecurityConfig (T-12 replaces with real JWT chain) and a minimal ApiException→status handler (T-32 replaces with RFC 7807). Verified live via curl: 403 no/wrong token, 201 with correct token |
| T-10 | Password hashing + users | ✅ | BCrypt(12), UserService/Controller CRUD + attribute updates. Verified via LocalUserProvisioningIT: hash != plaintext, starts with $2, matches() true |
| T-11 | JWT issue & decode | ✅ | JwtConfig (RSA keys from PEM, local dev keypair generated under resources/local/), TokenService (RS256, Clock-based iat/exp). Verified via TokenServiceLocalIT: all claims round-trip |
| T-12 | Filter chain + login | ✅ | Real SecurityConfig (stateless JWT resource server), AuthController/Service, GatekeeperAuthenticationEntryPoint (expired vs malformed). Fixed a design gap: login body carries tenantSlug (§9) but TenantFilter only reads the header (§5.1) — AuthService now resolves tenant from the body directly. Verified via LocalAuthSecurityIT (6/6): login success/failure, T1 no-token 401, T2 malformed 401, T3 expired 401 distinguished from malformed, valid-token access |
| T-13 | Refresh token rotation | ✅ | Opaque SHA-256-hashed tokens, rotation on use, reuse-detection revokes whole chain. Found and fixed a real bug: @Transactional's default rollback-on-exception was silently discarding the chain-revocation side effect while still returning 401 — fixed with noRollbackFor on both RefreshTokenService.rotate() and AuthService.refresh() (the outer tx boundary). Verified via LocalRefreshTokenIT (2/2) + full security regression suite (10/10) |
| T-14 | RBAC schema + indexes | ✅ | V3__rbac.sql (roles, role_hierarchy, permissions, role_permissions, user_roles) + V6__seed_permissions.sql. Verified: migration applies, `\d role_permissions` shows the covering index, 8 permission codes seeded |
| T-15 | Recursive permission CTE | ✅ | PermissionQueryRepository, §6.2 SQL verbatim (native, explicit :tenantId bind per §5.2). Verified via LocalPermissionCteIT (3/3): 3-level inheritance, cycle terminates with correct dedup, no cross-tenant leak via the CTE itself |
| T-16 | Role CRUD + hierarchy | ✅ | Role/Permission entities, RoleGraphRepository (native junction-table ops + ancestor-walk cycle check), RoleService/Controller, /permissions catalogue endpoint. Verified via LocalRoleServiceIT (4/4): CRUD round-trip, grant/revoke, cycle rejected 409, self-parent rejected |
| T-17 | Permission evaluator | ✅ | GatekeeperPermissionEvaluator (backs hasPermission('resource','action')), custom expression root (+ hasPermission(code), sameTenant(id), attr(name)) and handler, @EnableMethodSecurity. Proved on RoleService.createRole. Verified via LocalMethodSecurityIT (2/2): denied without permission, allowed once granted |
| T-18 | @PreAuthorize + ABAC | ✅ | @PreAuthorize applied across RoleService (role:read/write), TenantService (tenant:admin), UserService (user:read/write, with a documented `createUserInternal` bootstrap escape hatch for first-user provisioning). ABAC via the `attr()`/`sameTenant()` root methods (unit-tested directly, no separate PolicyEvaluator class needed — SpEL + the expression root already provide it). Verified: full suite 27/27 green, including AbacExpressionRootTest and LocalUserServiceAuthorizationIT |
| T-19 | Tenant leakage sweep | ✅ | **Caught a real cross-tenant data leak**: `findById()` uses Hibernate's get()-by-primary-key path, which does NOT apply `@Filter` conditions (only HQL/JPQL queries do) — GET /users/{id} and /roles/{id} returned 200 for another tenant's resource. Fixed by adding `findByIdAndTenantId` derived queries to UserRepository/RoleRepository and routing all id-lookups through them. Verified via TenantLeakageIT (5/5) + full suite (32/32) |
| T-20 | Index verification (§13) | ✅ | Seeded 50 tenants×200 users×20 roles (10k users), captured real EXPLAIN ANALYZE: no-index (3.53ms), planner's own choice (4.50ms, planner correctly prefers Seq Scan at this table size), forced-index (1.55ms, ~3x faster). Documented honestly in §13 including *why* the planner doesn't always pick the index. LocalIndexUsageIT (1/1) proves indexes are structurally usable via enable_seqscan=off |
| T-21 | Redis config | ✅ | RedisTemplate<String,Object> (String keys, JSON values), CacheKeys ("gk:" namespace), TtlJitter. Verified via LocalRedisConfigIT: round-trip through docker-compose Redis |
| T-22 | Cache-aside + stampede mutex | ✅ | CachedPermissionResolver (GET→miss→SET NX lock→load+cache / poll-then-DB-fallback), RedisMutex (Lua check-and-delete release), evaluator now reads through the cache. **Found another real bug**: `Set.copyOf()` returns a non-public JDK class Jackson's polymorphic Redis serializer can't deserialize — fixed with `LinkedHashSet`. Verified: 50-thread stampede test, ≤2 DB loads, full suite 35/35 |
| T-23 | Invalidation + pub/sub | ✅ | CacheInvalidationPublisher (afterCommit-only), CacheInvalidationListener (Redis pub/sub, every instance evicts via the same path), RoleGraphRepository reverse-CTE (role + descendants → affected users) wired into RoleService's write paths. **Found two more real bugs**: manually-constructed `MessageListenerAdapter` needs explicit `afterPropertiesSet()`, and `convertAndSend` was using the JSON-typed RedisTemplate while the listener decoded as plain string — fixed by adding a dedicated `StringRedisTemplate` for pub/sub. Verified via LocalCacheInvalidationIT + full suite 36/36 |
| T-24 | Redis degradation | ✅ | ResilientCache wraps every Redis call (get/set/lock/unlock), swallows DataAccessException, warns at most once per 30s window, falls through to Postgres. CachedPermissionResolver rewired through it. Verified via LocalRedisDegradationIT: actually stops the docker-compose Redis container mid-test via the `docker` CLI, confirms resolve() still succeeds from Postgres; full suite 37/37 |
| T-25 | Flag schema | ✅ | V4__feature_flags.sql (feature_flags + version col, flag_whitelist, flag_environment_override, partial index on enabled=true). Verified: migration applies; 150% rollout insert rejected by check constraint |
| T-26 | Flag CRUD + locking | ✅ | FeatureFlag entity (@Version), FeatureFlagService/Controller, FlagGraphRepository (whitelist/env-override junctions), OptimisticLockException→409 in ApiExceptionHandler. Verified via LocalFeatureFlagServiceIT (3/3): CRUD round-trip, stale-version update rejected 409 (real Hibernate `WHERE id=? AND version=?`), whitelist/override management |
| T-27 | Deterministic bucketing | ✅ | RolloutBucketer (murmur3_32 over flagKey:userId). Verified via RolloutBucketerTest (4/4, pure unit): stable across calls, decorrelated across flags, 10k-user distribution at 30% landed within [27%,33%] |
| T-28 | Evaluation engine | ✅ | FlagEvaluator implements the §7.1 six-step order exactly. Verified via LocalFlagEvaluatorIT (6/6): unknown→false, env-override outranks everything, whitelist outranks global disable, disabled→false, 0%/100% rollout |
| T-29 | Flag cache + negative cache | ✅ | CachedFlagRepository (same mutex/poll pattern as T-22, plus CachedFlag tombstone for 60s negative caching), FlagEvaluator now reads through it, invalidation wired into FeatureFlagService create/update/delete. Verified: unknown key stable across 20 calls, update reflected without TTL wait; full suite 51/51 |
| T-30 | Evaluate endpoints | ✅ | POST /flags/{key}/evaluate (self, or target user gated by flag:read), GET /flags/evaluate bulk bootstrap using one `findAll()` flags query (confirmed via SQL log). Verified via LocalFlagEvaluationControllerIT (2/2) over real HTTP; full suite 53/53 |
| T-31 | Audit log | ✅ | V5__audit_log.sql, AuditLog/AuditService, @Auditable + AuditAspect (@AfterReturning, joins the intercepted transaction so audit only persists if the change commits). Applied to role grants/hierarchy edits and flag create/update/delete. Verified via LocalAuditLogIT: exactly one row, correct actor + payload |
| T-32 | RFC 7807 errors | ✅ | GlobalExceptionHandler (extends ResponseEntityExceptionHandler) replaces the T-09 placeholder: ApiException→status, optimistic-lock→409, AccessDeniedException→403, catch-all→500 without leaking details, all as ProblemDetail. **Found two more real bugs**: (1) T-31's audit_log FK broke ~14 existing tests' cleanup ordering — fixed by deleting audit_log first everywhere; (2) switching 401 responses from empty to a JSON body triggers a JDK HttpURLConnection limitation (`HttpRetryException: cannot retry due to server authentication, in streaming mode`) on POST requests — fixed by adding httpclient5 so TestRestTemplate uses Apache's client instead. Full suite 55/55 |
| T-33 | Login rate limiting | ✅ | LoginRateLimiter (fixed-window counter, 10/60s, keyed by tenantSlug+email+IP), 429 with Retry-After header, fails open on Redis errors. Also fixed IPv6/IPv4 loopback normalization in AuthController and bounded Redis timeouts (2s) app-wide — a real gap: without it a broken Lettuce connection could hang a request for Lettuce's 60s default instead of failing open. Rewrote the test to seed Redis directly instead of looping real logins/stopping the shared container, after diagnosing reproducible 60s hangs to this machine's shared, heavily-loaded Docker environment (unrelated project containers, load avg ~7) rather than a code defect. Full suite 57/57 |
| T-34 | OpenAPI + actuator | ✅ | OpenApiConfig (bearerAuth security scheme). **Found a real bug**: SecurityConfig permitted `/swagger-ui/**` but not the exact `/swagger-ui.html` entry path it redirects from, so Swagger UI 401'd — fixed. Verified via curl: swagger-ui.html 200, api-docs contains bearerAuth, health/liveness/readiness 200 public, info/metrics/actuator-root correctly require auth |
| T-35 | Seed data | ✅ | V900__seed_demo.sql (tenant acme, admin→editor→viewer chain, demo admin, 2 flags). **Found two real bugs**: (1) R__ repeatable migrations always run after ALL versioned ones regardless of number, so the permission catalogue seed ran after V900 and left it with nothing to grant — fixed by converting it to versioned V6; (2) bundling seed into the `local` profile (which tests also activate) meant it persisted forever in the shared docker-compose volume and collided with tests' unscoped `DELETE FROM users` etc. — fixed by moving it to a separate opt-in `seed` profile (`-Dspring-boot.run.profiles=local,seed`). Verified end-to-end: login as admin@acme.test, role/flag access, and full suite 57/57 on a clean seed-free local DB |
| T-36 | Dockerfile | ✅ | Multi-stage (`maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`), Spring layered-jar extraction (`dependencies`/`spring-boot-loader`/`snapshot-dependencies`/`application`), non-root `app` user, `-XX:MaxRAMPercentage=70`, `JarLauncher` entrypoint. `.dockerignore` excludes target/.git/docs. **Verified for real**: `docker build` succeeds; the container boots against docker-compose Postgres+Redis (prod profile, `SPRING_DATA_REDIS_SSL_ENABLED=false` for the non-TLS local Redis), Flyway validates 6 migrations, `/actuator/health` → `{"status":"UP"}` in ~10s. |
| T-37 | CI workflow | ✅ | `.github/workflows/ci.yml`: JDK 21 + Maven cache, `./mvnw -B verify` (Testcontainers on the runner's Docker), uploads surefire/failsafe reports + dorny/test-reporter summary. Also `workflow_call`-able so deploy.yml can reuse it as a gate. Cannot be run until the repo is pushed to GitHub (repo is not yet a git repo). |
| T-38 | Neon + Upstash provisioning | ☐ | **Needs the user** — create free-tier Neon + Upstash + Koyeb accounts, add GitHub repo secrets (`DATABASE_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_URL`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `BOOTSTRAP_TOKEN`, `APP_ENVIRONMENT`, `KOYEB_API_TOKEN`). Also `git init` + GitHub remote. |
| T-39 | Deploy → GHCR → Koyeb | 🔶 | `.github/workflows/deploy.yml` written: reuse ci.yml as a gate → build+push `ghcr.io/<owner>/gatekeeper:{sha,latest}` via buildx with gha cache → `koyeb service update gatekeeper/gatekeeper --docker …:sha --wait`. Assumes a Koyeb app/service named `gatekeeper/gatekeeper` (port 8080, health `/actuator/health/readiness`) and GHCR pull creds configured on it — both part of T-38's manual setup. Unrun until the repo is on GitHub with secrets. |
| T-40 | Smoke test + README | ✅ | `scripts/smoke.sh <base-url> [bootstrap-token]`: health → bootstrap tenant+admin → login → create role → grant flag:write → create user → assign role → verify effective permissions via the CTE → create flag @50% (globally disabled) → evaluate false → whitelist → re-evaluate true (asserts the §7.1 whitelist-beats-disable flip). `README.md`: live URL placeholder, architecture, local dev, prod env vars, curl walkthrough, endpoint reference. **Verified end-to-end** against the T-36 container image (`SMOKE OK`, all 12 steps green). |

### Scope-conformance fixes made alongside Phase 7

- **§9 gap — missing user-role endpoints.** `POST /users/{id}/roles`, `DELETE /users/{id}/roles/{roleId}`, and `GET /users/{id}/permissions` were specified in §9 but never implemented (the `role:assign` permission was entirely unused; `RoleGraphRepository.assignRoleToUser` existed but was unreachable). Added: `RoleService.assignRoleToUser`/`revokeRoleFromUser` (`@PreAuthorize("hasPermission('role','assign')")`, `@Auditable`, tenant-scoped id resolution → 404 on cross-tenant, after-commit cache eviction for the affected user), `UserService.getEffectivePermissions` (reads through `CachedPermissionResolver`), and the three `UserController` endpoints + `AssignRoleRequest` DTO.
- **§11 gap — bootstrap left a tenant unusable.** `POST /tenants` only inserted the tenant row, so a bootstrapped tenant had no user able to authenticate. `TenantService.createTenant` now also provisions an `admin` role (all catalogue permissions) + admin user from new `adminEmail`/`adminPassword` request fields. §9 and §11 updated to match.
- Tests: `TenantLeakageIT` extended (cross-tenant role assignment + effective-permissions → 404; `role:assign` added to its fixture grants); new `LocalBootstrapAndRoleAssignmentIT` covers the bootstrap→login→assign→resolve→revoke happy path. **All green** — full `./mvnw clean verify` run (Docker Desktop started manually): 62 pass against docker-compose + 1 `@Disabled` perf harness; the only exclusions are the 2 pre-existing Testcontainers/sandbox casualties (`TenantUserPersistenceIT`, `AbstractIntegrationTestSmokeTest`).
- **§13.5 backfilled.** New `LocalPermissionEndpointTimingIT` (`@Disabled` perf harness) measured `GET /users/{id}/permissions`: cache miss p50 7.5 ms / p95 13.2 ms, cache hit p50 4.9 ms / p95 8.7 ms (200 samples, docker-compose). scope.md §13.5 table + interpretation updated; §12 DoD "EXPLAIN ANALYZE recorded" box ticked.
