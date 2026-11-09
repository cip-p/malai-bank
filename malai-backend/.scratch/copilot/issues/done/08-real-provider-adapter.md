# 08 — A real provider behind the seam

Status: ready-for-human
Category: enhancement

> **Flagged: not testable against synthetic data alone.** This is the only issue in the feature that needs something the repo cannot supply — a live provider and a key. Every other issue runs green with no external dependency. Keep it that way: the deterministic decider stays the default and remains the only implementation any test or e2e path touches.

## What to build

A second `CopilotDecider` implementation calling a real model provider, selected by property (`malai.copilot.decider`, default the deterministic one). The seam does not change; this is an implementation, which is the whole point of ADR-0012's shape and of screening's precedent.

- Honest recording: the provider's real model id and version, real latency, real cost in `costMicros` with the currency actually billed.
- The rendered prompt is unchanged — the redaction policy settled in the PRD is what leaves the estate, and it is already stored inline on every Entry for a deployer to read.
- The timeout budget property bounds the call; exceeding it is `TIMED_OUT`, already built in issue 05.
- Malformed or non-conforming provider output is a grounding failure or an unparseable verdict — both already built. No new failure vocabulary.
- Key handling belongs in `malai-infra`; nothing secret enters this repo.

The demo beat this unlocks: a real model runs over synthetic banking data, occasionally cites a record that was not in the Timeline, and the platform refuses to render the answer while recording the refusal.

## Acceptance criteria

- [x] The adapter is selected only by property; the deterministic decider is the default in every profile including test and e2e
- [x] With the property unset, no provider dependency is constructed and no key is required to boot
- [~] Recorded entries carry the provider's real model id, version, latency and cost — **built and pinned against a stubbed provider; the live-provider run is the one thing this repo cannot do** (see Resolution)
- [x] Timeout, error and grounding-failure paths behave as issue 05 defines them, with no new outcome vocabulary
- [x] The secret is provisioned in `malai-infra`, not here
- [x] `mvn install` green with the property unset

## Blocked by

- 07 (`07-e2e-recorded-chained-verifiable.md`)

## Comments

### Resolution (2026-07-22)

`AnthropicCopilotDecider` is the second implementation behind the seam, calling the Anthropic
Messages API through the official Java SDK (`com.anthropic:anthropic-java`, the same SDK the
provider publishes rather than a hand-rolled wire format). Selected by `malai.copilot.decider`;
`template` is the default everywhere, including test and e2e.

**Not done, and it is the flagged one.** Nothing here was run against a live provider — this
environment has no key and no outbound provider credential, and the issue's own banner says so.
Every claim below is proven against a WireMock-stubbed provider on a random port, which needs no
key and no network. What that does *not* prove is the one thing only a live run can: that the real
API accepts this exact request and answers in the shape assumed. The wire shapes were written from
the provider's current published SDK surface and verified against the SDK's own types, not guessed
— but a live smoke test remains the outstanding step, and the AC above stays open until someone
runs it with a real key.

Key decisions:

- **The seam's return type grew; its shape did not.** `explain` now answers with a `DeciderOutcome`
  carrying the claims, the model that actually answered, and what the invocation cost. The issue
  says "the seam does not change", and in every sense that matters it does not — one call, prompt
  in, claims out, no new failure vocabulary. But the AC "recorded entries carry the provider's real
  model id, version and cost" is unsatisfiable while those can only be per-decider constants: cost
  is per invocation, and the model that answers can differ from the one asked for. `modelId`,
  `modelVersion` and a new `costCurrency` stay as declared vals so a *failed* invocation still
  records which model was asked; the per-invocation facts ride back on the outcome and win when
  present.
- **The rendered prompt is the whole of what the adapter sends about the subject.** No system
  prompt, no prose of the adapter's own — the answer's shape is constrained by a JSON schema
  declared in the adapter, which carries structure and no subject data. So a deployer reading the
  stored Rendered prompt is reading the complete account of what left the estate, exactly as the
  PRD promises. Pinned by a test asserting one user message equal to the prompt and no `system`
  field.
- **The cited record type is deliberately left unconstrained in that schema.** Enum-constraining it
  would have the provider's own validator swallow half the fabrication the demo exists to show. A
  fabricated citation must reach Grounding and be refused there, not be quietly repaired upstream.
- **Cost is arithmetic over the tokens the provider billed**, input, output and both kinds of cache
  traffic, at per-token prices carried as configuration with the current published rates as
  defaults. Rounded up, so a recorded cost never understates the bill. Currency is the provider's
  (`USD`), not the platform's — the deterministic decider keeps its zero-priced `GBP` untouched.
- **Selecting the provider is what provisions its key.** Both deciders are conditional beans in one
  `CopilotDeciderConfiguration`; the provider's `@ConfigurationProperties` is bound inside a nested
  configuration under the same condition, so with the property unset nothing binds, no client is
  built, and no key is read. Naming the provider without a key fails at boot with a message naming
  the property, rather than at the first Explanation requested.
- **Retries are off by default and the SDK's read timeout is the decider budget.** The budget is the
  contract; a retry that outlives it produces `TIMED_OUT` anyway, so the adapter prefers one honest
  attempt. A read timeout is rethrown as `CopilotTimedOutException`, taking up issue 05's note that
  a provider surfacing its own timeout should keep that vocabulary rather than degrade to `ERRORED`.

Proven, not asserted:

- 22 new tests, 15 on the adapter against a stubbed provider and 7 on the selection. Every one was
  made to bite: nine mutations run then reverted — dropping the output schema, enum-constraining the
  cited record type, recording the model asked for instead of the one that answered, ignoring cache
  traffic in the cost, rounding the cost down, skipping the stop-reason check, making the
  deterministic decider need an explicit opt-in, binding the provider config unconditionally, and
  letting a read timeout degrade to a generic error. Each reddened exactly the tests that name it
  and no others. Both production files restored byte-identical.
- The existing 72 copilot tests stayed green through the seam change, including the acceptance test
  that pins the whole MDR request body — `modelId`, `modelVersion`, `costMicros: 0` and
  `costCurrency: "GBP"` are unchanged on the deterministic path.
- `openapi.yaml` is byte-identical to `main`, so there is no contract change; the CI oasdiff
  invocation (tufin/oasdiff v1.10.6 `breaking … --fail-on ERR`) exits 0 all the same.
- malai-infra `terraform fmt -check` and `terraform validate` both pass. A `plan` could not be run:
  this session cannot assume `mal-dev-terraform-role` (403 on `sts:AssumeRole`), and the dev
  runtime is hibernated regardless.

Out of scope, deliberately: no e2e path touches the adapter. Flipping `malai.copilot.decider` needs
a second Spring context, which `MalaiAcceptanceTestContext` boots once per JVM, and an e2e run
against the bundled stack would need a real key — which is the dependency this feature spent seven
issues avoiding.
