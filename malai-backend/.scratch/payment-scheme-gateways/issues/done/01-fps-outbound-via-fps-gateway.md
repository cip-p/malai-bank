# FPS outbound via `fps-gateway` (settle + return)

Status: done

## Parent

`.scratch/payment-scheme-gateways/PRD.md`

## What to build

The first tracer bullet: route FPS *outbound* payments through a new per-rail **Gateway** that bridges internal SQS and the external scheme's HTTP API, removing the stub's FPS-outbound SQS path in the same change. This slice carries the scaffolding the later rails reuse.

End-to-end behaviour: `payments-service` produces `payments-fps-command-submit` exactly as today; `fps-gateway` consumes it, maps the canonical `FpsOutboundSubmit` onto a recognizably scheme-shaped HTTP submit (debtor/creditor, pacs-like fields), and POSTs it to the external FPS scheme. The scheme returns a synchronous `202` ack and later calls the gateway's webhook with the outcome — settled or returned (with a return reason). The gateway maps the callback back onto `FpsOutboundSettled` / `FpsOutboundReturned` and produces the existing `payments-fps-event-settled` / `-returned` queue, so `payments-service` transitions `PENDING → SETTLED` / `RETURNED` unchanged.

`malai-stub` becomes the external FPS scheme over HTTP for the outbound path: it exposes the submit/ack endpoint, re-homes its existing `decide()` logic (magic-account directory, armed return, CoP-override→return) behind that handler, and fires the settle/return callback at the gateway via an outbound HTTP client. Its FPS submit `@SqsListener` and its settled/returned `SqsTemplate` producers are removed (atomic swap — FPS outbound never runs two paths at once). The stub keeps its remaining SQS code for FPS inbound, SWIFT, and BACS for now.

New modules: `malai-scheme-contracts` (shared scheme HTTP DTOs, sibling to `malai-payments-messages`) and `fps-gateway` (under `malai-services`, bundled into `malai-api`, no client-pair modules). Both registered in the root `pom.xml`; `fps-gateway` wired into `malai-api` (dependency + `@Configuration` import). The gateway is dumb and stateless — correlation IDs (`endToEndId`, `paymentId`) travel on the wire; no decision logic, no database.

`payments-service` and `PaymentsQueues` are not modified. Confirmation of Payee stays a direct `account-service → malai-stub` HTTP call.

## Acceptance criteria

- [x] `malai-scheme-contracts` module exists with the scheme-shaped FPS outbound submit/ack and settle/return callback DTOs, depended on by both `fps-gateway` and `malai-stub`.
- [x] `fps-gateway` module exists under `malai-services`, registered in root `pom.xml` (`<modules>` + `<dependencyManagement>`), wired into `malai-api`, with no `-client` / `-client-test-support` pair modules.
- [x] `fps-gateway` consumes `payments-fps-command-submit`, maps to a scheme-shaped HTTP submit, and POSTs to the external FPS scheme, expecting a `202` ack.
- [x] On the scheme's settlement callback, `fps-gateway` produces `payments-fps-event-settled`; on the return callback (with reason), it produces `payments-fps-event-returned` — both correlated by `paymentId`/`endToEndId`, FIFO group `paymentId`.
- [x] A customer FPS payment to a good beneficiary settles (`PENDING → SETTLED`); to a magic "closed" account it returns (`PENDING → RETURNED`) with the right reason; sending past a CoP name-mismatch returns with `ACCOUNT_NAME_MISMATCH`. (e2e: cross-bank known beneficiary settles, another-Malai-customer returns INVALID_ACCOUNT; stub acceptance tests assert AC04 closed + AC03 CoP-override.)
- [x] `malai-stub` exposes the FPS submit/ack HTTP endpoint, re-homes `decide()` behind it, and fires settle/return callbacks to the gateway over HTTP; its FPS submit `@SqsListener` and settled/returned producers are removed.
- [x] `payments-service`, `PaymentsQueues`, and Confirmation of Payee are unchanged.
- [x] `fps-gateway` acceptance tests (Kensa BDD) assert both edges: feed `payments-fps-command-submit` (SqsTemplate) → assert the scheme-shaped HTTP submit (WireMock) → POST the callback webhook → assert the produced `-event-settled`/`-returned`; every SQS message renders on the Kensa diagram.
- [x] `malai-stub` acceptance tests cover the HTTP submit → `decide()` → callback paths (MockMvc style), replacing the old FPS-outbound SQS round-trip tests.
- [x] Cross-stack e2e FPS outbound (settle + return) traverses `fps-gateway` with the real `malai-stub` as the external HTTP scheme, and passes. (Full e2e suite 19/19 green over the Docker stack incl. the new `fps-gateway` container.)
- [x] `mvn install` is green. (`mvn clean install` BUILD SUCCESS across all 34 modules.)

## Blocked by

None - can start immediately.

## Outcome

Delivered. Key decisions:
- Scheme contracts use simplified pacs-shaped JSON (debtor/creditor parties, instructed amount) with ISO-flavoured return reason codes (AC04/AC03/AB07/MD07/AC01). The `ReturnReason ↔ code` crosswalk lives once in `malai-scheme-contracts.FpsReturnReasonCodes`, shared by gateway and stub so they cannot drift.
- The async contract is honoured as a separate webhook call: the stub returns `202` then fires a settle/return callback to the gateway's `/fps/callbacks/{settlement,return}`. The stub fires it synchronously inside the submit handler (mirrors `StubSwiftScheme`'s blocking publish), keeping the propagated `X-TestId` on one thread.
- `fps-gateway` is bundled into `malai-api` AND ships its own Spring Boot app + Dockerfile to run as a standalone container in cross-stack e2e (each e2e service is its own container behind the in-JVM WireMock reverse-proxy; gateway on 8086, proxy prefix `/fps-gateway`).
- Security: the gateway carries no security config; spring-security-web is only on its test classpath (transitively via the acceptance harness), so the test boot helper excludes `SecurityAutoConfiguration`/`UserDetailsServiceAutoConfiguration` via `spring.autoconfigure.exclude` — NOT via an annotation on the app class (that leaks into `malai-api` and breaks its security chain).
- `MalaiApiArchitectureTest` extended to permit the per-rail gateway scheme-callback surface `/(fps|swift|bacs)/callbacks/**`; `MalaiSortCodePrefixLintTest` excludes `/fps-gateway/src/test/` (cross-bank fixtures); no production `/demo/*` diagnostic endpoint on the gateway (unlike the stub) to satisfy the malai-api arch rule.

Notes for next iterations (issues 02-04):
- The e2e FPS-outbound flow no longer renders the internal SQS hops on the Kensa diagram (the stub stopped recording FPS SQS; the gateway has no SQS-interaction recorder by design). The scheme HTTP hops (gateway↔stub) still render via the WireMock proxy. The two FPS-outbound `PaymentsTest`s dropped their `theSchemeSqsInteractionsAreRenderedOnTheDiagram` assertion; SWIFT/BACS/FPS-inbound keep it (still stub-SQS).
- `start-e2e-environment.sh` now includes `fps-gateway` in its `up --wait` set; the e2e stack must be migrated by `MalaiDbMigrator` before services boot (`ddl-auto=validate`).
- FPS inbound + return-to-sender are still on the stub's SQS path (issue 02): `StubFpsScheme.onReturn` `@SqsListener` and `StubInboundEmitter.emitFpsInbound` remain.
- `swift-gateway`/`bacs-gateway` (issues 03-04) reuse this pattern; `/swift/callbacks` + `/bacs/callbacks` are already allow-listed in the malai-api arch rule.
