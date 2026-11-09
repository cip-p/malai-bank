# 08 — E2E flow, diagram lane, and the tipping-off invariant

Status: done
Category: enhancement

## Parent

`.scratch/aml/PRD.md`

## What to build

The whole arc against the running stack, and the invariant that makes it a bank. An end-to-end test drives it live: fire the structuring pattern, watch the alert land within a sweep tick, rule the alerts, request the SAR draft, accept it, close as reported, and verify both hash chains — audit and MDR — walk clean over everything that just happened. The aml-service gets its party lane in the sequence diagrams, following the established classification conventions.

The tipping-off invariant becomes tested fact, not intention: customer-facing endpoints respond identically for a monitored account and an unmonitored one — same payloads, same states, same errors — asserted by sad-path acceptance tests; and an architecture rule forbids any customer-path module from depending on aml-service.

## Acceptance criteria

- [x] End-to-end test covers detection → dispositions → SAR draft → Gate close → chain verification over both scopes, green against the running stack
- [x] aml-service appears as its own party lane; diagram assertions pass
- [x] Tipping-off acceptance tests: monitored and unmonitored accounts are indistinguishable on customer surfaces
- [x] Architecture rule: no customer-path module depends on aml-service
- [x] `mvn install` green including the e2e module

## Blocked by

- `07-close-as-reported-through-the-gate.md`

## Comments

**Detection is live-fired, not armed.** `AmlTest` sends five £2,000 FPS payments to one
non-watchlisted beneficiary at another bank. Each is well under the £10,000 screening threshold,
so screening records five honest APPROVEs and lets them settle; the monitoring sweep then joins
them up and raises the structuring alert (riskScore 70, five evidence payments). The alert lands
within one sweep tick — the whole test, five rail round-trips included, runs in ~17s.

**The tipping-off invariant is tested in two places, at two altitudes.** `TippingOffTest`
(payments-service, sad package) reads the *same* payment twice — once with an OPEN case naming the
payer account, once with monitoring lifted — and asserts the two response bodies are equal, plus
the same for the 404 on an unknown payment. Each test also asserts the case row is actually there,
so the comparison cannot pass vacuously (mutation-checked: pointing the fixture at another account
turns it red). `AmlTest` then repeats the check live on the running stack: with the case open, the
customer reads their payment and gets the full strict payload with `state=SETTLED`.

**The architecture rule is stated as its strongest form.** `onlyTheAggregatorDependsOn` — nothing
outside `com.malai.aml` and the `com.malai.api` deploy aggregator may depend on aml-service.
Mutation-checked by pointing it at a nonexistent aggregator root, which correctly flags
`MalaiApiApplication`'s `@Import`. It lives in `MalaiApiArchitectureTest` because that is the only
scan that sees every module's production classes at once.

**Two diagram gaps this arc uncovered, both fixed.** `/api/v1/internal/payments` had no entry in
the e2e `toSystemLookup`, so aml-service's evidence reads were attributed to the catch-all
IdentityProvider party; and the two internal MDR read routes added in aml 05 / 07
(`GET .../decisions/{id}` and `.../actionable-output`) had no `HttpClassifier` entry, so they
rendered as "Unknown". The AmlTest diagram now has zero unlabelled arrows.

**Not done here, deliberately.** The tipping-off acceptance tests cover the payments customer
surface — the one AML actually observes. account-service's feed is covered structurally by the
architecture rule rather than behaviourally; if a future slice gives account-service any
AML-adjacent read, it should get its own `TippingOffTest`.
