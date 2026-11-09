# 10 — Real provider adapter for SAR drafting

Status: ready-for-human
Category: enhancement

## Parent

`../../PRD.md`

## What to build

The gated second half of the showpiece: a provider adapter behind the `SarDraftDecider` seam, property-selected and off by default in every profile — the deterministic template drafter remains the default everywhere, including test and e2e.

**Amended 2026-07-27** — the adapter targets the OpenAI-compatible chat-completions surface (`POST {base-url}/chat/completions`) rather than the Anthropic SDK. One adapter then serves any provider speaking that dialect, selected purely by `base-url` / `api-key` / `model` properties: local Ollama (the chosen smoke-test provider — free, keyless, grammar-enforced structured output), the Hugging Face Inference Providers router, vLLM, or a commercial endpoint later.

The adapter mirrors the Copilot provider adapter's shape: JSON-schema-constrained narrative output (`response_format: json_schema`), budget-bounded invocation with timeout recorded as `TIMED_OUT`, provider failure recorded as `ERRORED`, token-based cost arithmetic on the Model Decision. Refusal mapping differs on this surface: there is no labelled refusal stop reason, so an explicit `message.refusal`, a `finish_reason` of `length`, and output the platform cannot read each raise and are recorded as `ERRORED`.

Flagged HITL for the same reason the Copilot adapter was: the interesting behaviour isn't fully testable on synthetic data alone — enabling it needs a locally served model and a human judging real drafts before anyone demos them.

## Acceptance criteria

- [x] Adapter selected by `malai.aml.sar.decider=chat-completions`; default stays the template drafter in every profile
- [x] Adapter tests with a stubbed provider cover schema-constrained success, timeout → `TIMED_OUT`, refusal / `length` / unreadable output → `ERRORED`, and cost recording
- [x] A keyless provider (local Ollama) is called with no Authorization header; a keyed one carries `Authorization: Bearer` and the key never enters the prompt or body
- [x] Grounding validation applies to provider output identically — a stray citation is recorded and refused (grounding sits in `SarDraftService` above the seam, so it binds every decider; the armed-ungrounded acceptance tests pin the record-and-refuse behaviour)
- [x] Human smoke test against local Ollama on seeded case data, outcome noted in this issue's Comments
- [x] `mvn install` green with the adapter disabled

## Blocked by

- `06-sar-draft-grounded-decision.md`

## Comments

- 2026-07-27 — Provider decision made with the human: local-first via Ollama on the OpenAI-compatible surface, replacing the originally planned Anthropic SDK adapter. Rationale: unlimited free iteration for the smoke test, schema enforcement by grammar-constrained sampling, honest zero cost, no dependency on a torn-down dev runtime or paid key. The Hugging Face router (`https://router.huggingface.co/v1` + free HF token) remains reachable later through the same adapter by pointing `base-url` at it.
- 2026-07-27 — Implementation landed: `ChatCompletionsSarDraftDecider` + `ChatCompletionsDeciderProperties` (`malai.aml.sar.chat-completions.*`), selection wired in `SarDeciderConfiguration` behind `malai.aml.sar.decider=chat-completions` with a boot-time failure on missing/blank `base-url`. 17 WireMock adapter tests + 6 selection tests; full `mvn install` green (3578 tests, 0 failures) with the adapter disabled.
- 2026-07-27 — Pre-smoke probe against live Ollama (`qwen2.5:7b-instruct`, Intel Mac, 100% CPU): the adapter's exact request shape returned `finish_reason=stop`, schema-conformant statements, and every citation grounded — zero fabrication on the probe timeline. Throughput ~2 tokens/s; a full draft (375 prompt + 496 completion tokens) took ~4m10s, so the smoke test must raise `malai.aml.sar.decider-timeout-ms` (600000 suggested). Fallback if judging finds the wait unacceptable: `qwen2.5:3b-instruct`, faster but weaker prose.
- 2026-07-27 — Smoke-test runbook (human step remaining). Ollama is serving and the model is pulled. Run the stack with the adapter selected:
  `java -jar malai-api/target/malai-api-DEV-SNAPSHOT-exec.jar --malai.aml.sar.decider=chat-completions --malai.aml.sar.chat-completions.base-url=http://localhost:11434/v1 --malai.aml.sar.decider-timeout-ms=600000`
  (plus the usual local profile/datasource flags), seed the demo cases, then `POST /api/v1/admin/aml/cases/{caseId}/sar-draft` as an ops user and judge the narrative. Record the outcome here and tick the criterion.
- 2026-07-27 — Human smoke test passed. First attempt against the Marcus case recorded `TIMED_OUT` at the 10-minute budget — faithfully, on the Model Decision, with its latency — because CPU-only inference on this machine runs ~1.3-2 tokens/s; re-run with the budget at 20 minutes produced a draft the human judged acceptable. Verified done. How to run the model-backed drafter is documented in `docs/aml-live-fire-demo.md`.
