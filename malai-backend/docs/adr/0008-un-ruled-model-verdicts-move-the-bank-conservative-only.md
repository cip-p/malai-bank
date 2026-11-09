# Un-ruled model verdicts move the bank conservative-only

The MDR's first live consumer (payment screening in `payments-service`) forced a precise reading of "no model output may be actionable without a linked Human Disposition": read strictly it would make every screened payment wait for a human; read loosely it would let a model authorise money movement. We decided that an un-ruled Verdict may only move the consuming flow in the conservative direction — it can add friction (hold, flag), never remove it. When a verdict does not flag, the bank's standing default proceeds exactly as it would with no screening at all — the default acts, not the verdict. Moving a held payment anywhere (release or return) requires a Human Disposition, consumed as Actionable output through the gate. The model can stop money; only a human can move it. This rule binds every future Decision point, not just payment screening.

## Considered Options

- **Rule everything** — every screened payment waits for a Human Disposition before proceeding. Purest reading; destroys the customer payment experience and would force screening out of every latency-sensitive flow.
- **Pragmatic exception** — accept that proceed-on-APPROVE acts on un-ruled output and document it as demo pragmatism. Rejected: it hollows out the exact guarantee the MDR exists to demo.
