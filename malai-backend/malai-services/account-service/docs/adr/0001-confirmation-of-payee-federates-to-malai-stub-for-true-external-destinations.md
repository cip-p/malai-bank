# Confirmation of Payee federates to `malai-stub` for true-external destinations

Root ADR-0001 established that `POST /api/v1/confirmation-of-payee` answers "does the bank recognise this (sort-code, account-number, name) triple?" against the bank's directory, with Stance Y ("bank-knows-this-account") semantics and a `MIN_SIMILARITY_FOR_CLOSE_MATCH` of `0.85`. With the payments build-out (root ADR-0002, payments-service ADR-0001) malai now sends FPS payments to *non-Malai UK accounts* — destinations the malai directory does not and cannot know. This ADR extends ADR-0001: when the directory lookup misses, `account-service` makes a synchronous HTTP call to `malai-stub`, which holds a fictional other-bank directory and answers in the same verdict shape (`MATCH` / `CLOSE_MATCH` / `NO_MATCH` with `onRecordName`). The CoP contract surface returned to the webapp is unchanged.

## Considered options

- **Federate to `malai-stub` over HTTP** *(accepted)*. Models real CoP correctly: the protocol is bilateral, every participating bank holds its own directory, and Pay.UK's CoP network is the routing layer. Our stub plays the role of Pay.UK *and* the other-bank participants for demo purposes. HTTP — not SQS — because CoP is synchronous from the customer's POV: the verdict is rendered before they decide to send. Stub's directory contains demo accounts at fictional banks (NatWest, HSBC, Barclays, Lloyds, Monzo equivalents) with realistic names; the *same* directory powers stub-side FPS-outbound logic (a destination only resolves there when it's a known fictional account).
- **Always return `NO_MATCH` for unknown destinations** *(rejected)*. Honest to today's directory but breaks the highest-value demo moment: catching a name typo on an external send. Buyer asks "what does CoP do here?" and we'd have to say "it just gives up." That defeats the point.
- **Skip CoP for true-external destinations** *(rejected)*. UX would offer CoP only for malai-internal destinations and let external sends go blind. Inconsistent with real banking — CoP is *especially* about external sends.
- **Federate over SQS** *(rejected)*. Treats CoP as async messaging. CoP is on the user's blocking path (they're waiting to decide), so the round-trip latency tax has no upside. Reserve SQS for the genuinely async rail-message flows.

## Logic

```
POST /api/v1/confirmation-of-payee  (account-service)
  │
  ├─ Lookup (sort, account_number) in account_identifiers ⨝ accounts
  │      filtered to AccountType ∈ {PRIMARY, ADDITIONAL, SAVINGS} and OnboardingStatus = ACTIVE
  │      (no longer EXTERNAL — root ADR-0002 retires per-merchant EXTERNAL accounts)
  │
  ├─ If found:                       apply the ADR-0001 name-match rules (Stance Y, threshold 0.85)
  │
  └─ If not found:                   HTTP call to malai-stub `POST /cop/lookup`
                                     stub answers from its fictional directory with the SAME shape
                                     verdict passes through to the webapp unchanged
```

The threshold (`MIN_SIMILARITY_FOR_CLOSE_MATCH = 0.85`) and the anti-fishing posture from ADR-0001 apply identically on the stub side. The stub's directory is small (a handful of demo accounts per fictional bank), so the same anti-fishing residual limitation noted in ADR-0001 holds — a determined attacker with prior knowledge of a victim's name shape can still walk the threshold. The fix described there (bucket-based `CLOSE_MATCH` that doesn't leak `onRecordName`) is unchanged in scope: still out of scope for the demo, still tracked as a known limitation before any production-grade deployment.

## Consequences

- A new internal HTTP call from `account-service` to `malai-stub`. Goes through the existing `AbstractMalaiClient` pattern; the new `malai-clients/malai-stub-client` module pair holds the request/response types.
- CoP response latency increases by one HTTP round-trip when the destination is unknown to malai's directory. Negligible in the bundled JVM; would be a real concern in production, where the proper integration is to Pay.UK's CoP network directly.
- Stub directory is the source of truth for *which* fictional other-bank accounts CoP recognises. The same data drives the stub's FPS-outbound simulator (it knows the same set of accounts can receive payments) and its inbound-payment admin endpoint (the originator name can be drawn from this directory to look realistic). One directory, three uses.
- Root ADR-0001's Stance Y framing is preserved: malai answers "the bank knows this account" for accounts it itself holds, and `malai-stub` answers the same question for accounts at fictional other banks. The combined picture is "the relevant bank's directory" — exactly what bilateral CoP is in real life. The original anti-fishing analysis carries over without adjustment.
