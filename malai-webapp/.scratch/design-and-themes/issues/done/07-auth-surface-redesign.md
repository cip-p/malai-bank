# Auth surface redesign: Login, Forgot, Confirm, SignInHelp, Register

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The unauthenticated surface brought up to the same Starling/Chase-class polish as the authenticated app. After this slice lands, the first screen a demo audience sees — `/login` — reads as a designed bank, not a form on a white background, and every recovery / sign-up flow inherits the same visual treatment.

- **Shared auth shell**: a small layout used by every unauthenticated page — centred single column, the active-Theme wordmark at the top (placeholder until [08](./08-theme-catalogue-switcher.md) lands — render the static malai mark in the meantime), generous whitespace, focused form, prominent CTA. No primary nav, no bottom tabs. Inter + refreshed tokens inherited from [02](./02-typography-and-malai-refresh.md).
- **LoginPage**: first-impression treatment — wordmark, headline ("_Sign in to malai_"), the existing email + password fields in the new design system, primary "Sign in" CTA, secondary "_Forgotten your password?_" and "_Need help signing in?_" links. The existing auth flow and validation behave identically.
- **ForgotPasswordPage / ConfirmForgotPasswordPage**: same shell, headlines that explain the step, the existing `ConfirmCodeField` retained and styled into the new system.
- **ConfirmRegisterPage**: same shell. `ConfirmCodeField` retained. Resend code affordance preserved.
- **SignInHelpPage**: same shell. Content polish only.
- **RegisterLayout + RegisterAboutPage / RegisterAddressPage / RegisterCredentialsPage**: existing three-step layout retained — do **not** refactor onto `<StepFlow>` in this slice (that would duplicate the StepFlow extraction work; the existing layout is internally analogous and already covered by tests). Pages get token + typography polish only.
- All existing form schemas (Zod from `openapi:gen`), submit handlers, focus-on-error behaviour, error-mapping (per [ADR-0007](../../docs/adr/0007-anti-enumeration-error-policy.md)) unchanged.
- All existing page tests pass after the redesign — they assert on visible roles and labels, not on Tailwind classes.

## Acceptance criteria

- [ ] Shared auth shell renders a centred single-column layout with the malai wordmark at top on Login, Forgot, ConfirmForgotPassword, ConfirmRegister, SignInHelp, and the three Register step pages.
- [ ] LoginPage renders headline + email + password + primary CTA + secondary forgot/help links; existing sign-in flow + validation unchanged.
- [ ] ForgotPasswordPage, ConfirmForgotPasswordPage, ConfirmRegisterPage retain the existing `ConfirmCodeField` styled into the new system.
- [ ] Resend-code affordance on ConfirmRegisterPage preserved.
- [ ] SignInHelpPage renders the polished content; no behavioural change.
- [ ] RegisterLayout retains its three-step structure (NOT migrated to `<StepFlow>` in this slice); each step page inherits new tokens + typography.
- [ ] All existing form behaviours — validation, focus-on-error, error mapping — pass unchanged.
- [ ] All existing component tests for these pages pass after the redesign; assertions on visible roles / labels / text, not on class names.
- [ ] axe-core a11y spec extended over each redesigned auth page without violations.
- [ ] No comments anywhere.

## Blocked by

- [02-typography-and-malai-refresh](./02-typography-and-malai-refresh.md)
