# AML: the live-fire demo

Two postures, one dataset. The **museum** is what a self-guided evaluator finds already on the AML
worklist after a reset. The **live fire** is the beat a presenter runs on stage: five sub-threshold
payments, and an alert lands within one sweep tick.

There is no hidden mechanism in either. The typology rules are the trigger, they run on a timer, and
the alert you watch appear is the one the rules produced.

## The sentence this demo is built to prove

> Screening stops the £10,000 payment before the money moves. AML catches it split into five
> £2,000 payments after the money has moved. The customer never learns either happened.

## Reset

```bash
./demo-reset-local.sh      # seeder JAR against malai_local
```

`reset` is `clean → migrate → seed` and is safe to re-run: the seeded MDR entries replay under fixed
entry ids and append nothing new, `clean` removes the seeded cases (including any you live-fired
onto a seeded account last time), and `seed` rebuilds them.

## What the reset leaves on the worklist

`GET /api/v1/admin/aml/cases` as an admin (the ops reviewer, `priya.sharma@example.com`) shows two
cases. Both subjects are ordinary retail customers who can log in.

**Priya Raghunathan** — `priya.raghunathan@example.com`, account `00000000-0000-4000-8000-000022000014`,
case `00000000-0000-4000-8000-000022000015`, status `CLOSED_REPORTED`.

The finished arc, and the strongest single screen in the feature. One `STRUCTURING` alert over five
£2,000 payments to one beneficiary — each honestly under the £10,000 threshold, jointly at it —
risk score 70. The alert carries an `ACCEPT` ruling. The case carries a SAR draft recorded as its
own Model Decision, citing the alert and every payment it fired over and nothing else, with an
`ACCEPT` ruling of its own; the narrative on the case is the one that ruling made filable.

**Marcus Delacroix** — `marcus.delacroix@example.com`, account `00000000-0000-4000-8000-000022000024`,
case `00000000-0000-4000-8000-000022000025`, status `OPEN`.

The same story mid-flight, and the case to open if someone asks "what does the officer actually
do?". Two alerts, neither ruled: a `STRUCTURING` alert over four £2,600 payments, and a
`HIGH_RISK_CORRIDOR` alert over one £7,500 SWIFT payment to a `QM` beneficiary — that one pins the
jurisdiction list version (`2026-07-01`) by content hash, so "which list said so, on which day" is
answerable from the record.

Both cases' evidence payments settled two to five hours before the reset: inside one rolling window
when they were made, outside the current one now, so the museum never competes with what you fire
live.

## The live fire

Fire from **Priya**. Her case is terminal, so the pattern you fire opens a *new* case — the
worklist visibly grows by one — and her seeded funding leaves headroom to spare for the £10,000
the pattern moves.

**What to say while it runs:** each payment is £2,000. Screening looks at one payment at a time,
with no balances and no history, so all five are honestly under the threshold and all five go
through. Nothing is wrong with any individual payment. It is the pattern that is wrong, and only
something reading *across* settled payments can see it.

### Preconditions

- The stack is running and `GET /ready` is healthy.
- A reset has run.
- You are signed in as Priya. Two ways in:
  - **Webapp** — log in at the app with `priya.raghunathan@example.com` and enter the emailed code. The
    branded code email is part of the pitch; the `+aml1` alias lands in the same demo mailbox as
    every other seeded persona.
  - **API** — seeded personas keep a permanent Cognito password (`DemoAmlReported!2026`, see
    `AmlDemoSeed`) for non-prod test infrastructure, authenticated **directly against the Cognito
    pool** with `USER_PASSWORD_AUTH`, never through the Malai API (auth-service ADR-0001 as amended
    by malai-infra ADR-0002). Take the access token from that response.

### The five payments

Send five identical payments to one beneficiary at another bank. `60-00-01 / 12345678` is the
stub's NatWest entry, which settles.

From the webapp: **Send money** → new payee → sort code `60-00-01`, account `12345678`, name
`Alice Walker` → £2,000 → send. Repeat five times.

From the API:

```bash
for i in 1 2 3 4 5; do
  curl -sS -X POST "$MALAI_API/api/v1/payments" \
    -H "Authorization: Bearer $PRIYA_ACCESS_TOKEN" \
    -H "MAL-Account-Holder-Id: 00000000-0000-4000-8000-000022000011" \
    -H "Idempotency-Key: demo-aml-structuring-$i" \
    -H "Content-Type: application/json" \
    -d '{
          "fromAccountId": "00000000-0000-4000-8000-000022000014",
          "toSortCode": "60-00-01",
          "toAccountNumber": "12345678",
          "toName": "Alice Walker",
          "amountMinorUnits": 200000,
          "currency": "GBP",
          "description": "Transfer"
        }'
  echo
done
```

Each returns `201` with `"state": "PENDING"`. They settle over FPS within a few seconds — that is
the point worth narrating: **the money is gone before anything detects anything.**

### What to watch, and when

| Beat | When |
|---|---|
| Five payments accepted, `PENDING` | immediately |
| All five `SETTLED` | seconds |
| Monitoring sweep re-reads the last rolling hour | every 10s (`malai.aml.sweep.cron`) |
| A new `OPEN` case for Priya's account on the worklist | within one tick of the fifth settlement |

Refresh `GET /api/v1/admin/aml/cases` (or the Ops worklist) until the new case appears. The
end-to-end test that fires this same pattern against a running stack — five payments, five rail
round trips, the sweep, and the officer's whole path afterwards — completes in about 24 seconds, so
payments-to-alert lands well inside half a minute. The sweep interval is a latency budget, not a
promise: poll for the case, don't count seconds.

Open the new case. It shows one `STRUCTURING` alert, risk score 70, and the five payments you just
sent as its evidence — each with the content hash that pins it, so "these are the payments, and
they have not been doctored" is a property of the record rather than a claim.

### Carry on into the officer's path

The new case is a real case. From here you can rule the alert through the MDR worklist, request the
SAR draft, accept it, and close as reported through the Gate — the same arc the Priya museum case
already shows finished. Closing as reported before ruling the SAR is refused with a domain-coded
409, which is worth doing deliberately if the audience includes a compliance officer.

## Drafting the SAR with a real model

By default, everywhere, the SAR narrative comes from the deterministic template drafter: instant,
free, and identical on every run. A real model sits behind the same `SarDraftDecider` seam as an
opt-in per run — the adapter speaks the OpenAI-compatible chat-completions surface, so anything
serving that dialect works. Locally that is Ollama.

### Start the model

Once per machine:

```bash
brew install ollama
ollama pull qwen2.5:7b-instruct
```

Each session:

```bash
ollama serve
curl http://localhost:11434/api/generate \
  -d '{"model":"qwen2.5:7b-instruct","keep_alive":"120m"}'
```

The second command loads the model into memory and pins it there, so the first draft doesn't pay
the ~5 GB cold load. Re-run it whenever `ollama ps` shows nothing.

### Start the API with the adapter selected

```bash
java -jar malai-api/target/malai-api-DEV-SNAPSHOT-exec.jar \
  --malai.aml.sar.decider=chat-completions \
  --malai.aml.sar.chat-completions.base-url=http://localhost:11434/v1 \
  --malai.aml.sar.decider-timeout-ms=1200000
```

plus your usual local flags. Selection is per-process and nothing else changes: leave the
properties off and the template drafter is back. A different model is
`--malai.aml.sar.chat-completions.model=...`; a hosted provider (Hugging Face router, vLLM, a
commercial endpoint) is a different `base-url` plus
`--malai.aml.sar.chat-completions.api-key=...` — same adapter throughout.

### What to expect

Request the draft as usual (`POST /api/v1/admin/aml/cases/{caseId}/sar-draft`). On a CPU-only
machine a 7B model generates at roughly 1.5–2 tokens/s, so a real draft takes **10–15 minutes** —
that is why the budget above is 20 minutes where the template default is 10 seconds. Watch progress
in the Ollama server log (`n_decoded` climbing in the `print_timing` lines). On anything with a
GPU the same draft takes seconds.

Two failure shapes are worth showing rather than hiding:

- **The budget expires** — the invocation is abandoned and the Model Decision records `TIMED_OUT`
  with its latency and zero cost. The record *is* the demo: the platform tells the truth about the
  model not answering in time.
- **The model cites a record outside the case timeline** — grounding fails, the whole draft is
  withheld, and the decision records the raw output alongside `GROUNDING_FAILED`. Smaller models do
  this more often, which makes the guardrail easier to demonstrate, not harder.

The chain and cost columns stay honest throughout: a local model records zero cost because the
per-token rates default to zero; point the adapter at a paid provider and set
`--malai.aml.sar.chat-completions.input-micros-per-million-tokens` (and `output-…`) to see real
spend land on the record.

## The tipping-off beat

Do this one immediately after the alert lands, while the case is open.

Log in as Priya in a second browser (or read her payment back through the API). Her app is an
ordinary bank app: the five payments are in her feed as settled payments, her balance is what it
should be, and there is no flag, no message, no delay, and no altered error anywhere on any customer
surface. Nothing tells her she is under investigation, because no customer-facing service is even
able to ask — no customer-path module may depend on `aml-service`, and an architecture rule fails
the build if one does.

That is not a demo courtesy. Tipping off is a criminal offence in the UK, and it is asserted by
tests: `TippingOffTest` reads the same payment twice, once with a case open naming the payer and
once with monitoring lifted, and compares the two response bodies.

## If nothing appears

- **Payments still `PENDING`** — the stub isn't running or isn't reachable. Nothing settles, so the
  sweep has nothing to read; monitoring only ever sees settled facts.
- **Payments `RETURNED`** — you sent to a returning destination. Check the beneficiary against
  `GET /api/v1/sandbox/counterparties`; use one whose outcome is `SETTLES`.
- **All five settled, no case after a minute** — check the joint total actually reaches £10,000
  (five × 200000 minor units) and every leg is under it, that all five went to the *same*
  beneficiary sort code and account number, and that they landed inside one rolling hour.
- **A case appeared but attached to an existing one** — you fired from an account that already had
  an `OPEN` or `INVESTIGATING` case. One case per account is the rule; fire from Priya, whose case
  is closed, or close the open one first.
- **Re-running the curl loop does nothing** — the `Idempotency-Key`s are the same, so each request
  replays its cached response and no new payment is created. Change the key prefix to fire again.
- **Firing again inside the same hour adds an alert rather than a case** — correct, twice over. The
  account already has an open case, so the new alert attaches to it; and because the rolling window
  now holds all ten payments to that beneficiary, the group is a genuinely different evidence set,
  so it is a genuinely different alert. What dedup suppresses is the *re-sweep*: the same five
  payments re-evaluated every ten seconds hash to the same evidence set and raise once, so the
  worklist reflects patterns rather than polling cadence.
