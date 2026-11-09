# malai-backend

The banking API behind Malai: a Kotlin / Spring Boot Maven multi-module project. Every service
(auth, accounts, ledger, payments, cards, payees, standing orders, direct debits, insights, MDR,
AML, …) has its own module and can be deployed independently. Postgres with Flyway migrations is
the store, SQS carries the payment-rail messaging (Localstack outside AWS), and authentication
goes through AWS Cognito, replaced by a local stub for development.

Tests are Kensa BDD (`given / whenever / then`), backed by WireMock for downstream calls. Each
module writes an HTML report to `<module>/target/reports/index.html`.

## Prerequisites

- JDK 25
- Docker
- `lsof` (used by the local start scripts; present on macOS and most Linux distributions)

Maven doesn't need installing: `./mvnw` downloads the pinned version (3.10.0) on first use and
checks its SHA-256.

## Run the tests

```bash
./start-docker-containers-local.sh   # Postgres on :5432 and Localstack SQS on :4566; recreates malai_test
./mvnw install                       # compile and run every test (end-to-end tests are skipped)
```

`start-docker-containers-local.sh` is safe to re-run. It recreates the `malai_test` database each
time but leaves `malai_local`, the database the locally-running app uses, untouched.

Run a single test class or method:

```bash
./mvnw test -pl malai-services/auth-service -Dtest=AuthControllerTest
./mvnw test -pl malai-services/auth-service -Dtest=AuthControllerTest#login
```

## Run the end-to-end tests

The end-to-end tests drive the real `malai-api` and `malai-stub` containers through their HTTP
APIs, with the Cognito stub running inside the test JVM.

```bash
./mvnw install -DskipTests                            # once, so the test-support modules are in ~/.m2
./malai-end-to-end-tests/start-e2e-environment.sh     # builds the jars, starts the docker-compose stack
./mvnw test -pl malai-end-to-end-tests -De2e.skip=false
./malai-end-to-end-tests/stop-e2e-environment.sh
```

The stack uses ports 5433 (Postgres), 8080 (`malai-api`), 9091 (`malai-stub`) and 4567
(Localstack), and the test JVM's WireMock listens on 9090. That overlaps with the locally-running
app below, so stop one before starting the other.

## Run the app locally for malai-webapp

```bash
./start-docker-containers-local.sh   # if the containers aren't already running
./start-malai-api-local.sh           # builds, then runs malai-api on :8080 in the foreground
```

The script also starts two helpers in the background and stops them when `malai-api` exits:

- `malai-stub` on :9090 simulates the outside world: the payment schemes (FPS, SWIFT, BACS,
  cards) and the Confirmation of Payee directory.
- `malai-cognito-stub` on :9095 stands in for AWS Cognito, so register, login, refresh and
  sign-out all work offline. It accepts any code: `123456` after registering, `12345678` at
  login. Users persist in `~/malai-cognito-stub`.

To load the demo data, run this in a second terminal:

```bash
./demo-reset-local.sh
```

It deletes only the rows it seeded on a previous run, applies any pending migrations, and seeds
the demo users and their banking history again. Anything else in `malai_local` is left alone.
The seeded users' emails are in `malai-seeder/src/main/kotlin/com/malai/seeder/data/`.

Then start malai-webapp (`npm run dev` in `../malai-webapp`). It runs on `http://localhost:5173`
and calls the API at `http://localhost:8080`, which allows that origin.

Use `--no-build` on either script to skip the Maven build. Run `./malai-db.sh local` to drop and
re-migrate `malai_local` from scratch.

## CI

`.github/workflows/malai-backend.yml` has two jobs:

- **build** runs the module tests against Postgres and Localstack service containers.
- **end-to-end-tests** runs the docker-compose stack and the end-to-end tests.

Both upload their Kensa reports as workflow artifacts.

## Layout

| Path | What it is |
|---|---|
| `malai-api/` | The aggregator that bundles every service into one Spring Boot app. Holds the hand-authored `openapi.yaml` contract. |
| `malai-services/` | One module per bounded context (`auth-service`, `ledger-service`, `payments-service`, …). |
| `malai-clients/` | A client module per service for internal calls, each paired with a `*-test-support` module of WireMock fixtures. |
| `malai-commons/` | Shared infrastructure: security, SQS, test and acceptance-test bases, ArchUnit rules. |
| `malai-sql/` | Flyway migrations and the migrator CLI used by `malai-db.sh`. |
| `malai-stub/` | The simulated outside world (payment schemes, Confirmation of Payee). |
| `malai-cognito-stub/` | The local stand-in for AWS Cognito. |
| `malai-seeder/`, `malai-registration-seeder/` | Demo-data seeding. |
| `malai-end-to-end-tests/` | End-to-end tests and their docker-compose stack. |
| `docs/adr/` | Architecture decision records. |

`CLAUDE.md` covers the coding and testing conventions in more depth.
