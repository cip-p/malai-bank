---
status: accepted (supersedes ADR-0001)
---

# The bank reaches each scheme through a per-rail Gateway; `malai-stub` becomes HTTP-only

ADR-0001 put `malai-stub` on the far end of the SQS queues *as* the scheme — it consumed `command-*`, ran the settle-vs-return logic, and produced `event-*`. This supersedes that wiring. We insert a per-rail **Gateway** (`fps-gateway`, `bacs-gateway`, `swift-gateway`) bundled into `malai-api` that owns the SQS↔HTTP bridge: it consumes the `command-*` queues `payments-service` produces, maps each canonical message onto a recognizably scheme-shaped HTTP request, submits it to the external scheme, and on the scheme's asynchronous callback produces the matching `event-*` queue. `malai-stub` stops touching SQS entirely and instead exposes the external schemes' HTTP APIs. The reason is realism: SQS is an internal AWS primitive that would never cross a bank's trust boundary to Pay.UK or SWIFTNet, and real banks run exactly these per-rail gateways. The swap-in seam a buyer cares about moves from "the SQS counterparty" to "the external HTTP scheme," which is where a real integration actually sits.

## Considered options

- **Per-rail Gateway, bundled in `malai-api`, internal SQS + external HTTP, stub HTTP-only** *(accepted)*. Internal-messaging on one side, scheme-protocol HTTP on the other — the shape a bank technologist recognises as an FPS gateway / Bacs submission service / SWIFT connector. Bundled into `malai-api` (like every other `malai-services` module) keeps infra flat; the "separate deployable on the other side of the rail" authenticity is already carried by `malai-stub`.
- **Keep ADR-0001 (stub is the scheme over SQS)** *(rejected)*. Less infrastructure, already working. But a buyer's payments architect notices a bank "talking SQS to Pay.UK," which undercuts the headline pitch exactly where they look hardest. The gateway is the honest seam.
- **One unified `scheme-gateway-service`** *(rejected)*. Fewer modules, and consistent with ADR-0002-of-`payments-service`'s rejection of per-rail *lifecycle* services. But the rails genuinely differ (FPS near-real-time messaging, BACS batch-style, SWIFT store-and-forward), three named gateways read more like a real bank, and the cost is contained because the gateways need **no `-client`/`-client-test-support` pair modules** — the gateway↔scheme boundary is the external seam, not an internal malai call.
- **Three separate gateway deployables** *(rejected)*. Most authentic to a bank's separate systems, but three new ECS services + SQS perms + networking for a demo. Bundling into `malai-api` is the pragmatic call.
- **Synchronous outcome on the submit HTTP response** *(rejected)*. Simplest, one round-trip. But real schemes are asynchronous, and an ack-plus-callback flow also exercises the gateway's inbound webhook surface — which inbound payments need regardless.
- **Full ISO 20022 external messages (pacs.008/pacs.002/camt)** *(rejected)*. Maximally authentic but schema-heavy XML. "Recognizably scheme-shaped" (debtor/creditor, pacs-like fields, reason codes) buys the credibility at a fraction of the build cost.

## Shape, contracts, and decisions

- The gateway is a **dumb, stateless translator**: it maps message *shape* (canonical ↔ scheme-shaped) but makes no *decisions*. All simulation logic — settle-vs-return, magic accounts, armed returns, CoP-override→return, inbound generation — stays in `malai-stub`, now triggered over HTTP. The gateway has no database; `endToEndId`/`paymentId` correlation travels on the wire.
- The external HTTP contract is **async**: submit returns a synchronous `202` ack; settlement/return arrives later as a callback to the gateway's webhook. The gateway then produces the `event-*` queue. Inbound arrives as a scheme webhook → `event-received` queue.
- The canonical↔scheme-shaped wire contracts live in a new **`malai-scheme-contracts`** module (sibling to `malai-payments-messages`, which stays the internal canonical SQS contracts) so the gateway and `malai-stub` agree on the format without the stub depending on gateway code, and internal-canonical stays visually distinct from external-scheme.
- **Confirmation of Payee stays a direct `account-service → malai-stub` HTTP call.** CoP is a synchronous lookup, not a queued rail message; routing it through a gateway (an SQS↔HTTP rail-message bridge) adds nothing. `malai-stub` presents one coherent FPS-scheme HTTP surface — CoP for `account-service`, rail submit/ack/callback for `fps-gateway`.

## Testing

- **Gateway acceptance tests** (Kensa BDD) compose existing patterns: feed the `command-*` queue (`SqsTemplate`) → assert the gateway's scheme-shaped HTTP submit (WireMock, the house downstream stub) → POST the gateway's callback webhook → assert the `event-*` queue (capture-listener + `StateCollector`). Both SQS edges and both HTTP edges are asserted, and every SQS message renders in the BDD doc via `SqsInteractionCapturer`. WireMock is the external system at acceptance; `malai-stub` is the external system at cross-stack e2e.
- `payments-service`'s own tests are untouched — they already fake the far end with `SqsTemplate` (`theStubScheduleSettlesPayment`), so they don't care that a gateway now occupies that role. `PaymentsQueues` is frozen.

## Migration

Rail-by-rail vertical slices, **FPS first** (the only rail with the full settle/return/inbound surface — proving the hardest rail de-risks BACS inbound-only and SWIFT always-settles). Each slice builds the gateway, adds the stub's HTTP endpoints for that rail, and removes that rail's old stub-SQS code, all in one e2e-tested slice. `malai-stub` transitionally mixes SQS (un-migrated rails) and HTTP (migrated rails) but never runs two paths for the same rail; the demo is green at every merge. The final slice removes the stub's last SQS dependency.

## Consequences

- Three new modules under `malai-services`: `fps-gateway`, `bacs-gateway`, `swift-gateway`, each bundled into `malai-api` (dependency + `@Configuration` import) and registered in the root `pom.xml` `<modules>` + `<dependencyManagement>`. No paired client modules.
- A new `malai-scheme-contracts` module holds the scheme-HTTP-contract types, sibling to `malai-payments-messages`; both the gateways and `malai-stub` depend on it.
- `malai-stub` loses its SQS code and dependencies (`malai-sqs`, Localstack) by the end of the migration, and gains per-scheme HTTP controllers (submit/ack), an outbound callback client, and an inbound-trigger path that calls the gateway webhook. `StubFpsScheme.decide()` survives, re-homed behind HTTP.
- The gateways take over SQS queue provisioning for the payments queues via the existing `QueueCatalog` mechanism in `malai-commons` (already on `malai-api`'s classpath).
- `malai-infra`: `malai-stub`'s ECS task loses SQS permissions; `malai-api` is unchanged (already has SQS). Recorded here for the infra repo.
- Supersedes ADR-0001 of `payments-service`.
