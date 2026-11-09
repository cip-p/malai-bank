# CLAUDE.md (malai)

## Project

**Malai** is a reference UK retail banking platform, built so prospective bank operators can evaluate the architecture before commissioning it. It is a multi-app product (website, web app, backend, infrastructure) designed to _look and behave_ like a production-grade UK bank without being one. The end user is a bank executive or technologist evaluating the platform, not a retail customer.

**Core value:** a bank decision-maker — watching a demo, or registering and exploring on their own — concludes: _"this looks like a real bank, and the architecture beneath it is one we'd actually want to run."_ Every other priority bends to that outcome.

This file owns cross-module concerns only. When you work inside a module you inherit this file **plus** that module's `CLAUDE.md`; module-local docs win on module-local questions.

## Modules

| Folder           | Purpose                  | Stack                                                                        |
| ---------------- | ------------------------ | ---------------------------------------------------------------------------- |
| `malai-backend/` | Banking API and services | Maven multi-module, Spring Boot, Kotlin, Postgres, Flyway, SQS, Kensa BDD    |
| `malai-webapp/`  | Customer and staff app   | Vite, React 19, TanStack Query, React Router, shadcn/ui, Tailwind v4, Vitest |
| `malai-website/` | Marketing site           | Astro 7, React islands, Tailwind v4, Playwright, Lighthouse CI               |
| `malai-infra/`   | Cloud infrastructure     | Terraform on AWS; ECS Fargate runs `malai-api`                               |

Each module has its own GitHub Actions workflow in `.github/workflows/`, and those workflows build and test only: there are no release or deploy jobs in this repository.

The backend bundles its services (`auth`, `account`, `account-holder`, `card`, `payee`, `payments`, `direct-debit`, `standing-order`, `insights`, `ledger`, `sandbox`, `mdr`, `exec`, `copilot`, `aml`) plus the simulated rail gateways (`fps`, `bacs`, `swift`, `card`) into a single deploy unit, `malai-api`. Details in [malai-backend/CLAUDE.md](malai-backend/CLAUDE.md).

## Cross-module contracts

**OpenAPI** is the contract between `malai-backend` and `malai-webapp`.

- Source of truth: `malai-backend/malai-api/src/main/resources/static/openapi.yaml`, **hand-authored**, served at `GET /openapi.yaml` by a running backend.
- The webapp keeps a snapshot at `malai-webapp/openapi.yaml` and generates its TypeScript client from it (`npm run openapi:gen`). When the contract changes, copy the backend's file over the snapshot. Nothing checks automatically that the two copies match.
- Rule: when adding a domain, edit `openapi.yaml` **first**, then implement controllers to match. Keep changes backwards-compatible.

**Auth** is cookie-based (HttpOnly + Secure + SameSite=Lax) with an `Authorization`-header fallback. Set by `auth-service`, validated by `JwtDecoderFilter` in `malai-commons/malai-security`. The webapp talks to the API with credentials-included fetch.

**Local wiring.** The webapp's dev server on `:5173` calls `malai-api` on `:8080` directly (CORS allows that origin). `./start-malai-api-local.sh` also runs `malai-stub` on `:9090` and `malai-cognito-stub` on `:9095`, so sign-in works with no AWS account, and sets cookies without `Secure` so Safari keeps them over `http://localhost`. Postgres (`:5432`) and Localstack SQS (`:4566`) come from `./start-docker-containers-local.sh`.

## Cross-cutting invariants

- **No comments in code, anywhere — including tests.** Prefer naming and structure over commentary. Applies to all four modules.
- **Idempotency**: production write endpoints take an `Idempotency-Key` header. The shared mechanism lives in `malai-commons/malai-security` (`IdempotencyInterceptor`). The package has an open critique — surface it before adding new uses.
- **Audit chain**: append-only, currently behind a single global chain lock. Acceptable for demo scale; revisit per-shard chaining when load demands it. Retention policy is pending — partitioning by `ts` is in place, the archive flow is not.
- **Service boundaries**: each backend service has a matching `malai-clients/<domain>-client` module for internal call-outs, with `*-test-support` for WireMock fixtures. Don't reach across services without going through the client.

## Testing posture

- **Backend** — Kensa BDD tests (`given/whenever/then`). Tests need Postgres and Localstack; `./start-docker-containers-local.sh` from `malai-backend/` brings them up and wipes `malai_test`. Run all with `./mvnw install`, scope with `-pl <module> -Dtest=<ClassName>[#method]`.
- **Cross-stack E2E** — `malai-backend/malai-end-to-end-tests/` runs against the docker-compose stack of `malai-api` and `malai-stub`; start and stop scripts live in that module.
- **Webapp** — Vitest and Testing Library for unit and integration tests. There is no browser-level suite.
- **Website** — Playwright against the production build (accessibility, content security policy, SEO, forbidden terms); Lighthouse CI as a local ship gate.
- **Infra** — `terraform fmt` and `terraform validate` on every unit.

Testing is a first-class concern across all modules. Match the existing patterns when adding new tests; don't invent a second testing style in a module that already has one.

## Agent skills

Planning runs through Matt Pocock's skills, chiefly `grill-with-docs`. Each module tracks its work as local markdown issues under `.scratch/<feature>/`, records decisions in `docs/adr/`, and keeps its domain glossary in `CONTEXT.md` (the backend has a `CONTEXT-MAP.md` pointing at per-service `CONTEXT.md` files).

## Navigation

- Cross-module concerns: edit this file.
- Module commands, conventions and layouts: that module's `CLAUDE.md` and `README.md`.
