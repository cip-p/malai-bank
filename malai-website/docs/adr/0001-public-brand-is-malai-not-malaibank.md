# Public brand on the website is "Malai", not "MalaiBank"

The internal product and workspace name is _MalaiBank_. On the public-facing website we use **Malai**. Using the word "Bank" commercially in the UK is regulated under FSMA and draws PRA/FCA attention to firms not authorised to take deposits — which we explicitly are not. Calling the marketing brand _Malai_ keeps the regulatory surface narrow while preserving the name's recognisability inside the [[Webapp]] sandbox, where the visitor is clearly in a demo context and seeing "MalaiBank" reinforces the bank-like feel we want them to evaluate.

## Considered options

- **Keep "MalaiBank" everywhere.** Strongest brand continuity, highest regulatory risk — public marketing of an unauthorised entity using "Bank" is the exact pattern the FCA targets.
- **Drop "Bank" everywhere, including the webapp.** Lowest risk, but kills the demo's "this looks like a real bank" payoff that the entire pitch rests on.
- **Split the brand (chosen).** Public site is _Malai_; the demo product retains _MalaiBank_ inside its own UI. The website's framing makes the demo context unambiguous before the visitor sees the word _Bank_.

## Consequences

- All website copy, hero, meta tags, OG images, sitemap, and outbound communications use _Malai_.
- The [[Webapp]] continues to render as _MalaiBank_ internally — no change required there.
- If the brand decision is revisited (e.g. the project pursues actual authorisation, or counsel asks for further distance), the website is the single place to update.
- Known minor inconsistency: the live URL `malaibank.com` and the contact address `info@malaibank.com` both leak "MalaiBank". Acquiring a `malai.*` domain was considered and deferred — the cost of migration outweighs the residual exposure of a domain string that no visitor would interpret as a regulatory statement.
