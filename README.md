# Gatekeeper

A production-grade, **monolithic** service for hierarchical permissions, organization
tenancy, and runtime feature toggling — no restarts required.

> **Source of truth:** [`doc/scope.md`](doc/scope.md). Task breakdown: [`doc/session.md`](doc/session.md).
> If reality diverges from the scope doc, the doc changes first.

- **Live URL:** _set after T-39 deploy_ — `https://<koyeb-app>.koyeb.app`
- **API docs:** `/swagger-ui.html` on the live URL (bearer-auth "Authorize" button)
- **Health:** `/actuator/health/{liveness,readiness}`

---

## What it does

| Pillar | Summary |
|---|---|
| **Tenant isolation** | Every tenant-owned row carries `tenant_id`; a Hibernate `@Filter` injects the tenant predicate into every JPA query. Native queries (the permission CTE) bind `tenant_id` explicitly. Cross-tenant reads return **404, never 403**. |
| **RBAC + ABAC** | Roles inherit from parent roles. Effective permissions are resolved through the inheritance graph with a **recursive CTE** in Postgres, enforced at the method level via `@PreAuthorize` and a custom expression handler (`hasPermission`, `sameTenant`, `attr`). Attribute rules (SpEL over the JWT `attrs` claim) layer on top, deny-by-default. |
| **Feature flags** | Six-step evaluation order (env override → whitelist → global disable → deterministic percentage rollout), mutable at runtime through the API under optimistic locking. |

**Cross-cutting engineering focus:** cache-aside on Redis with a stampede mutex and
event-driven invalidation; index-backed CTE traversal with `EXPLAIN ANALYZE` evidence
([`doc/scope.md` §13](doc/scope.md)); a Testcontainers integration suite proving auth
failure, token expiry, and absence of tenant leakage.

## Architecture

```
client ──HTTPS──▶ Koyeb web service (1 instance, Spring Boot 3.3 / Java 21)
                        │  JDBC/TLS         │  rediss://
                        ▼                   ▼
                  Neon Postgres 16    Upstash Redis 7
                  (source of truth)   (cache + locks; optional at runtime)
```

- **Persistence:** PostgreSQL 16, Flyway migrations run on boot in every environment.
- **Cache:** Redis via Lettuce. Every Redis call is wrapped — a Redis outage degrades
  latency, never correctness; requests fall through to Postgres.
- **Auth:** self-issued RS256 JWTs (15-min access token + rotating refresh token).
- **Build/CI:** Maven, GitHub Actions (`./mvnw verify` with Testcontainers on the runner).
- **Deploy:** push to `main` → image to GHCR → `koyeb service update`.

## Local development

Everything (app, Postgres, Redis) runs against `docker-compose.yml` — no Neon/Upstash
account needed.

```bash
docker compose up -d                 # postgres:16 on :55432, redis:7 on :6379
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# with demo seed data (tenant "acme", admin@acme.test / admin123, two flags):
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,seed
```

Run the full test suite (needs a running Docker daemon for Testcontainers):

```bash
./mvnw verify
```

Build the container image:

```bash
docker build -t gatekeeper:local .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:55432/gatekeeper \
  -e DB_USER=gatekeeper -e DB_PASSWORD=gatekeeper \
  -e REDIS_URL=redis://host.docker.internal:6379 \
  -e BOOTSTRAP_TOKEN=local-dev-bootstrap-token \
  -e JWT_PRIVATE_KEY="$(cat src/main/resources/local/jwt-private.pem)" \
  -e JWT_PUBLIC_KEY="$(cat src/main/resources/local/jwt-public.pem)" \
  gatekeeper:local
```

## Configuration (prod profile)

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | JDBC URL for the Neon **pooled** endpoint (`?sslmode=require`) |
| `DB_USER`, `DB_PASSWORD` | Neon credentials |
| `REDIS_URL` | `rediss://default:<password>@<host>:6379` (Upstash) |
| `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` | PEM RSA keypair (raw PEM, not a path) |
| `BOOTSTRAP_TOKEN` | shared secret required by `POST /tenants` |
| `APP_ENVIRONMENT` | `prod` — consumed by the feature-flag environment-override rule |

## API walkthrough

Base path `/api/v1`. Errors are RFC 7807 `application/problem+json`.
See [`scripts/smoke.sh`](scripts/smoke.sh) for the same flow as an automated check:

```bash
BASE=https://<koyeb-url>
TOKEN_ENV=... # your BOOTSTRAP_TOKEN

# 1. Bootstrap a tenant + its first admin (admin role holds every permission)
curl -sS -X POST "$BASE/api/v1/tenants" \
  -H 'Content-Type: application/json' -H "X-Bootstrap-Token: $TOKEN_ENV" \
  -d '{"slug":"acme","name":"Acme Inc","adminEmail":"admin@acme.test","adminPassword":"change-me-please"}'

# 2. Log in
TOKEN=$(curl -sS -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"tenantSlug":"acme","email":"admin@acme.test","password":"change-me-please"}' | jq -r .accessToken)
AUTH=(-H "Authorization: Bearer $TOKEN")

# 3. Create a role and grant it flag:write
ROLE=$(curl -sS -X POST "$BASE/api/v1/roles" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"name":"flag-manager"}' | jq -r .id)
curl -sS -X POST "$BASE/api/v1/roles/$ROLE/permissions/flag:write" "${AUTH[@]}"

# 4. Create a user, assign the role, inspect effective (CTE-resolved) permissions
USER=$(curl -sS -X POST "$BASE/api/v1/users" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"email":"dev@acme.test","password":"another-strong-pw"}' | jq -r .id)
curl -sS -X POST "$BASE/api/v1/users/$USER/roles" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"roleId\":\"$ROLE\"}"
curl -sS "$BASE/api/v1/users/$USER/permissions" "${AUTH[@]}"      # ["flag:write", ...]

# 5. Create a flag at 50% rollout, evaluate, whitelist, re-evaluate
curl -sS -X POST "$BASE/api/v1/flags" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"flagKey":"new-checkout"}'
V=$(curl -sS "$BASE/api/v1/flags/new-checkout" "${AUTH[@]}" | jq -r .version)
curl -sS -X PUT "$BASE/api/v1/flags/new-checkout" "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d "{\"enabled\":false,\"rolloutPercentage\":50,\"version\":$V}"
curl -sS -X POST "$BASE/api/v1/flags/new-checkout/evaluate?userId=$USER" "${AUTH[@]}"   # {"enabled":false}
curl -sS -X PUT "$BASE/api/v1/flags/new-checkout/whitelist/$USER" "${AUTH[@]}"
curl -sS -X POST "$BASE/api/v1/flags/new-checkout/evaluate?userId=$USER" "${AUTH[@]}"   # {"enabled":true}
```

Run the automated smoke test against a live deployment:

```bash
BOOTSTRAP_TOKEN=<token> scripts/smoke.sh https://<koyeb-url>
```

## Endpoint reference

Full table in [`doc/scope.md` §9](doc/scope.md). Key groups:

- `POST /auth/login` · `/auth/refresh` · `/auth/logout`
- `POST /tenants` (bootstrap) · `GET|PATCH /tenants/me`
- `/users`, `/users/{id}`, `/users/{id}/attributes`, `/users/{id}/roles`, `/users/{id}/permissions`
- `/roles`, `/roles/{id}`, `/roles/{id}/permissions/{code}`, `/roles/{id}/parents/{parentId}`, `/permissions`
- `/flags`, `/flags/{key}`, `/flags/{key}/whitelist/{userId}`, `/flags/{key}/overrides/{env}`,
  `/flags/{key}/evaluate`, `/flags/evaluate` (bulk)
