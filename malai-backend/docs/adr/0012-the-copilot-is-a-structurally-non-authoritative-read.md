# The Copilot is a structurally non-authoritative read

The Copilot explains why something happened, and a bank's architect will ask what stops that explanation becoming a decision. We decided to answer structurally rather than by policy: there is deliberately **no `malai-copilot-client` module**, so in a codebase where every cross-service call goes through a client pair and `noCrossModuleInternalAccess` forbids reaching around one, no service *can* consume an Explanation; and **Grounding is all-or-nothing** — one Claim citing a record outside the assembled Timeline and the whole answer is refused, recorded as a COMPLETED Model Decision with its Raw output stored and its Verdict absent. The cost is a missing module that looks like an omission, and an occasional produced-but-unshown answer.

## Considered Options

- **Render the grounded Claims and drop the ungrounded ones** — the reviewer still gets most of the timeline. Rejected: a silently truncated narrative is more misleading than none, because the reader cannot tell what was removed or why.
- **Render everything with ungrounded Claims flagged** — most faithful to what the model actually said. Rejected: it puts an uncited assertion in front of an investigator on a surface whose entire promise is that every claim is traceable.
- **Contract and UI wording only** — free, and honest as far as it goes. Rejected: nothing would stop a future service taking a dependency on the Copilot, which is exactly what the audience probes.
- **Have the MDR Gate refuse Advisory decisions outright** — belt and braces. Rejected: it would make `Advisory` mean "forbids something", complicating the Gate's single rule that the latest ruling wins, to defend against a consumer that structurally cannot exist.

## Consequences

The copilot ArchUnit test records the *absence* of a client pair as a deliberate architectural fact, so the gap is not later "fixed" by a well-meaning agent. A future feature that genuinely needs machine-readable explanations must revisit this ADR rather than quietly add the missing module. Human Dispositions may still be appended to a Copilot decision — the record allows it, nothing waits on it — which keeps the entries compatible with the case-management disposition flow arriving next.
