# Staff Surfaces pin the default token set and sit outside the Theme catalogue

ADR-0011 frames the Theme catalogue as three fictional customer bank brands (`malai`, `halcyon`, `linden`) switchable by the User. The Ops and Exec **Surfaces** deliberately do not participate: both pin the default `malai` token set, offer no theme switcher in their chrome, and share only **Mode** (light/dark) with the Banking surface.

A real bank's back office is not customer-brand-skinned, and the constancy is itself a pitch beat: switch the customer app from `halcyon` to `linden` mid-demo, walk into `/ops`, and the platform beneath is visibly the same — brands are skins, the record plane is the platform's. Pinning the existing default also costs zero new CSS and keeps the WCAG-AA audit surface at two Mode variants per staff page instead of six theme/mode combinations.

Rejected: a dedicated fourth "ops" token set (strongest ops-tool feel, but a token set outside the catalogue's fictional-brand framing with its own AA burden — revisit only if the pinned default proves visually wrong for dense worklist chrome), and full catalogue participation (cheapest to wire, but a rebranding back office reads as theatre — the opposite of "the hats are real").

Consequence: whatever mechanism scopes `data-theme` for the staff shells must override the User's persisted catalogue choice while they are on a staff Surface and restore it on return to Banking.
