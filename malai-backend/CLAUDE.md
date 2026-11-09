# CLAUDE.md (malai-backend)

Maven multi-module Spring Boot service. Kotlin. Postgres + Flyway. Tests use Kensa BDD with WireMock for downstreams. In production the whole thing ships as one `malai-api` JVM on ECS.

## Commands

```bash
# Bring up Postgres + Localstack SQS (wipes malai_test and the broker on every run; safe to re-run)
./start-docker-containers-local.sh

# Build and run all tests
./mvnw install

# Tests only
./mvnw test

# Single test class / method (note -pl module path)
./mvnw test -pl malai-services/auth-service -Dtest=AuthControllerTest
./mvnw test -pl malai-services/auth-service -Dtest=AuthControllerTest#login
./mvnw test -pl malai-clients/malai-account-client -Dtest=AccountsOnboardingClientTest

# When stale target/ causes Kensa "Did not find declaration for class …" or
# unresolved-reference errors, wipe and rebuild:
./mvnw clean install

# End-to-end tests against the docker-compose stack (needs a prior `./mvnw install` so the
# test-support modules are in ~/.m2; uses ports 5433, 8080, 9090, 9091, 4567)
./malai-end-to-end-tests/start-e2e-environment.sh
./mvnw test -pl malai-end-to-end-tests -De2e.skip=false
./malai-end-to-end-tests/stop-e2e-environment.sh

# Run malai-api locally for malai-webapp (:8080, with malai-stub :9090 and malai-cognito-stub :9095)
./start-malai-api-local.sh
./demo-reset-local.sh          # seed demo data into malai_local
```

## OpenAPI contract

`malai-api/src/main/resources/static/openapi.yaml` is the **hand-authored** contract, served at `GET /openapi.yaml`. The webapp generates its TypeScript client from it.

**Rule**: when adding or changing a domain, edit `openapi.yaml` first, then implement controllers to match. Keep changes backwards-compatible — nothing in CI checks this any more.

## Module map

Maven multi-module project. Modules registered in `<modules>` of root `pom.xml`.

```
malai-commons/
  malai-commons/                  StubbedTimeService, UUIDGenerator, MalaiCode/HttpExceptionHandler — shared production infrastructure
  malai-security/                 SecurityConfig (cookie + header constants), JwtDecoderFilter (cookie-first / header-fallback), ServiceJwtFilter, MalaiUser, CognitoClient
  malai-archunit-rules/           ArchUnit rules enforcing architectural invariants
  malai-test-commons/             DatabaseCleanup, TestUUIDGenerator, JWT support
  malai-acceptance-test-commons/  MalaiAcceptanceTest base, MalaiAcceptanceTestContext (per-JVM singleton), Kensa fixtures, WireMock holder, HttpClientStub
malai-sql/                        Flyway SQL migrations + MalaiDbMigrator CLI
malai-clients/
  malai-clients-common/                       AbstractMalaiClient (Apache HC5 wrapper), config
  malai-clients-test-support-common/          WireMock + Kensa fixtures, TestBuilder, JsonSerialiser
  malai-account-client/                       AccountsOnboardingClient (internal)
  malai-account-client-test-support/
  malai-account-holder-client/                AccountHolderOnboardingClient (internal)
  malai-account-holder-client-test-support/
  malai-auth-client-test-support/             Request builders for auth endpoints
malai-services/
  auth-service/                   register / confirm / login / refresh-token / signout. Cookie + header dual mode.
  account-service/                internal account onboarding
  account-holder-service/         internal account-holder + individual + address onboarding
  payee-service/                  Confirmation of Payee + payee directory
  direct-debit-service/           direct-debit mandates
  standing-order-service/         standing orders
malai-api/                        Spring Boot aggregator — bundles all services into one JVM (the prod deploy unit)
  src/main/resources/static/openapi.yaml   the hand-authored contract
malai-end-to-end-tests/           E2E tests across the running stack
malai-seeder/                     dev-only seeder; rebuilds demo state in malai_local via ./demo-reset-local.sh
malai-cognito-stub/               dev-only WireMock stand-in for AWS Cognito; started by the local scripts, never deployed
malai-registration-seeder/        seeder for registration-flow demo data
```

**Module pair pattern** — every API domain has a paired `*-client` (production code) and `*-client-test-support` (WireMock stubs + builders). Shared infrastructure lives under `malai-commons/`.

## Architecture

**Deployment topology** — in production every Spring service is bundled inside the `malai-api` JVM (the only thing ECS deploys). Internal cross-service calls loop back over `http://localhost`. Per-service modules still exist so each service has its own boundaries (controllers, configuration, tests); `malai-api` declares them all as dependencies.

**HTTP clients** — all clients extend `AbstractMalaiClient` (wraps Apache HttpComponents 5). A shared `CloseableHttpClient` is built by `MalaiClientConfiguration` from `MalaiConfigurationProperties` (`malai.base-url`, `malai.user-agent`). Customisations (e.g. test interceptors) go through `MalaiHttpClientCustomizer` beans — no bean overriding. Client methods follow the shape: build the request → `makeRequest(request, expectedStatusCode)` → `fromJson()` the body.

**Auth** — cookie-based session (`access_token` + `refresh_token` HttpOnly cookies, `Secure`, `SameSite=Lax`, no `Domain` attribute) with `Authorization: Bearer` header as a fallback for legacy mobile/CLI clients. Set by `auth-service`, validated by `JwtDecoderFilter` in `malai-security` (cookie wins over header). The webapp uses the cookie path with credentials-included fetch.

**Currency** — service entities must use `com.malai.commons.model.Currency` (enum) for currency fields, annotated `@Enumerated(EnumType.STRING)`. Never `String`. When mapping to a response DTO that uses `String`, call `.name` on the enum value.

**Controller security** — all controller methods that handle authenticated user actions must:
- Inject the authenticated user via `@AuthenticationPrincipal user: MalaiUser` (or the narrower `MalaiUserForUserIdOnly` / `MalaiUserWithAccountHolderId`).
- When looking up a resource by UID, **also** filter by `user.accountHolderId` to enforce ownership. Never look up by UID alone.
- Services verify ownership (e.g. `findByAccountIdAndAccountHolderId`) and throw `NotFoundException` if the resource does not belong to the authenticated account holder.

Routes that forward the raw access token to a downstream (e.g. `signout`) use `AuthCookies.readAccessToken(request)` rather than reading the `Authorization` header directly — that helper handles both cookie and header transparently.

## Coding conventions

- **No comments.** Including in tests. Prefer naming and structure over commentary.
- **Kotlin style** — data classes for production and test models; `lateinit var` for non-nullable reference types in builders; `Delegates.notNull<T>()` for primitive types in builders (never default values like `0` or `false`); private constructors with companion-object factory methods (`anAccount()`, `aSavingsGoal()`); fluent builder API via `apply { }`.

## Testing

The most important code in this repo. New service code is BDD-tested at the acceptance level; client modules have their own WireMock-backed tests.

**Project conventions override generic TDD-skill advice.** When the global `/tdd` skill's examples conflict with the patterns below, the patterns below win. Specifically:
- Full-entity **database assertions** (`and(theUserInTheDatabaseIs(..., UserEntity(...)))`) are the *required* pattern — not "bypassing the interface." The intent is that any new entity field forces the test to be updated.
- Kacey `responseEntity().containsProp(json { ... noExtraProps() })` is the response-payload pattern. `noExtraProps()` is non-negotiable.
- Kensa BDD (`given() / when() / then() / and()`) is the test style throughout. The generic skill's Jest-style examples translate to this DSL, not vice versa.
- The TDD loop in the skill (red → green → refactor, vertical slices, behavior-not-implementation) still applies — only the *examples* change.

**Acceptance test base** — every service-level acceptance test extends `MalaiAcceptanceTest`, which composes `WithKotest`, `WithWiremock`, `WithApplicationContext`, `WithStubbedTime`, `WithCognitoJwks`, `WithCognitoJwtToken`, `WithServiceJwtToken`, and `MalaiTestFixtures`. The shared infra (Spring context, WireMock, RestClient) is bootstrapped exactly once per JVM by `MalaiAcceptanceTestContext.boot(...)` from each service base class's `companion object { init { } }`.

`MalaiAcceptanceTestContext.httpClient` is a singleton `RestClient` shared by every test in the JVM, configured with **cookie management disabled** on the underlying Apache HC5 client so `Set-Cookie` responses don't leak between tests (a regression that previously broke 13 auth-service tests in one batch — keep it disabled).

**Composable `With*` interfaces** — provide WireMock stub methods. Each method takes request parameters (`accessToken`, `accountUid`, …) as direct arguments; the response body is a response builder (`AccountsResponseBuilder`, `FeedItemsResponseBuilder`, …). Never mix request parameters and response body in one builder.

**Response builders** implement `TestBuilder`, live in a `builders` package within `*-test-support`:
- Top-level: `class FooResponseBuilder private constructor() : TestBuilder`
- Nested builders for sub-objects as inner classes
- `build()` serialises to JSON via `JsonSerialiser.toJson()`
- Inner data classes represent the JSON structure

**Kensa BDD** — `given() / when() / then() / and()`:

```kotlin
given(getAccountsReturnsTheFollowingAccounts(
    someAccessToken,
    anAccounts().withAccounts(
        anAccount().withCurrency(someCurrency).withAccountUid(someAccountUid))))

whenever(clientCallsGetAccounts(someAccessToken))

then(theLastGetAccountsResponseIs(expectedResponse))
and(madeTheHttpRequest(getRequestedFor(...)))
```

**Response payload assertions** with Kacey — `responseEntity().containsProp(json { ... noExtraProps() })`. Strict-match all fields, fail on unexpected extras. `noExtraProps()` at every level (top and nested `json {}`). For nested objects: `json { }`; for arrays: `listOf(json { ... })`.

Standard service-level controller pattern:
```kotlin
then(theLastResponseHas(
    status().isOk,
    responseEntity().containsProp(json {
        "accountHolderUid" To someAccountHolderUid().toString()
        "accountHolderType" To "INDIVIDUAL"
        noExtraProps()
    })
))
```

Nested arrays/objects:
```kotlin
then(theLastResponseHas(
    status().isOk,
    responseEntity().containsProp(json {
        "accounts" To listOf(
            json {
                "accountUid" To someAccountUid().toString()
                "currency" To "GBP"
                noExtraProps()
            }
        )
        noExtraProps()
    })
))
```

Client-level pattern is the same; use `something` for dynamic/random values:
```kotlin
then(theLastResponseHas(
    status().isOk,
    responseEntity().containsProp(json {
        "userId" To someUserId().toString()
        "authToken" To something
        "expiresIn" To 3600
        noExtraProps()
    })
))
```

**Database assertions** — always compare against the full expected entity, not individual fields. Forces tests to update when entities gain fields.
```kotlin
and(theUserInTheDatabaseIs(someUserId(), UserEntity(
    id = someUserId(),
    email = "some-email@example.com",
    name = "some-name",
    registrationConfirmed = false,
    createdAt = Instant.parse("2026-10-15T10:30:00Z"),
    createdBy = someUserId().toString()
)))
```

**Test fixtures** — shared constants in `MalaiTestFixtures` (`someAccessToken`, `someAccountUid`, `someCurrency`, …).

**Auth in acceptance tests** — `WithCognitoJwtToken` exposes `HttpRequestBuilder.withJwtTokenForUserId(userId)` which adds an `Authorization: Bearer …` header signed by the stubbed Cognito JWKS. To exercise the cookie path, set `Cookie: access_token=…` (or `refresh_token=…`) via `.header("Cookie", …)`.

**WireMock** runs on a random port per JVM, booted by `MalaiAcceptanceTestContext`. Per-service `runXxxService` boot helpers thread `wiremock.url(...)` into downstream-base-URL properties on the application context.

**Annotations:**
- `@MalaiClientSpringTest` — client module tests (loads `MalaiClientTestApplication`)
- `@SpringTest` — service module tests (full Spring Boot context, random port, test profile)

**Repository tests** — any `*Repository` that defines custom functions must be tested.

## Adding a new API

1. **Edit `openapi.yaml` first** — exact paths, request/response schemas, field names. Controllers come next.
2. **Create the service module** — `{domain}-service` under `malai-services/`.
3. **Create the client pair** — `malai-{domain}-client` and `malai-{domain}-client-test-support` under `malai-clients/`.
4. **Register modules in root `pom.xml`** — both `<modules>` and `<dependencyManagement>`. A module declared only in `<dependencyManagement>` won't build, and consumers will fail to resolve the JAR.
5. **Wire the service into `malai-api`** — add a dependency in `malai-api/pom.xml` and import its `@Configuration` so the aggregator picks up the controllers.
6. **Implement the client** — extend `AbstractMalaiClient`.
7. **Models** — Kotlin data classes matching the schema.
8. **Test support** — `With{Domain}` interface + response builders.
9. **Tests** — request construction (method, URL, headers, body) and response deserialisation.
10. **`./mvnw install`** to validate everything compiles and passes.

## Agent skills

### Issue tracker

Issues live as markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical roles (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`) recorded as a `Status:` line in each issue file. See `docs/agents/triage-labels.md`.

### Domain docs

Multi-context: `CONTEXT-MAP.md` at the repo root points to per-service `CONTEXT.md` files under `malai-services/<domain>-service/`. See `docs/agents/domain.md`.
