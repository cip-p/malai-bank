# Keyboard-only Playwright spec on the demo path

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

A single Playwright test that walks the demo path using only the keyboard, asserting that focus order and skip-link work end-to-end. After this slice lands, the project has a verifiable a11y claim beyond axe-core's automated coverage — the keyboard journey a bank-exec demo walks is regression-protected. This is the explicit extension to [ADR-0006](../../docs/adr/0006-a11y-baseline.md) committed to in the PRD.

- New `tests/e2e/keyboard.spec.ts`. Reuses the shared Cognito `storageState` from `tests/e2e/auth.setup.ts` (matches the existing e2e pattern; do not log in from inside the test).
- The spec walks:
  1. From `/` (AccountsPage), Tab to the first quick-action (Send money), Enter to navigate to `/move/send`.
  2. Tab forward through the Send money step 1 (source picker), select an Account with Space, advance to step 2 with the primary CTA via Enter.
  3. Step 2 (destination): pick **My accounts** mode, select a target Account, advance.
  4. Step 3 (amount + reference): type an amount, advance.
  5. Step 4 (confirm): the **Send** CTA is focusable; activate with Enter; success screen appears; the post-send CTA returns focus correctly.
  6. From the success screen, navigate back to AccountsPage (Tab to the CTA, Enter).
  7. From AccountsPage, Tab to an Account card, Enter to open `/accounts/:accountUid`.
  8. From AccountDetailPage, Tab to the first transaction row, Enter to open `<TransactionDetailSheet>`; assert focus moves into the sheet; Escape closes the sheet and asserts focus returns to the originating row.
  9. Tab to the bottom-tab "Profile" (mobile viewport) or to the top-right user menu (desktop viewport), and reach Sign out.
- The spec runs at **two viewports**: desktop (default Playwright) and mobile (an iPhone-sized device descriptor). The mobile run additionally asserts that the bottom tab bar is keyboard-reachable.
- For each navigation, the spec asserts that the document's `activeElement` matches an expected target (the page `<h1>`, the focused CTA, the focused row). It uses only `Tab`, `Shift+Tab`, `Enter`, `Space`, `Escape`.
- The "Skip to main content" link (existing `SkipToMainContent`) is exercised at least once: focus the body via Tab from the top, verify the link is visible, activate it with Enter, assert focus lands on `<main id="main">`.

## Acceptance criteria

- [ ] `tests/e2e/keyboard.spec.ts` exists and runs as part of the `npm run test:e2e` suite (no separate npm script).
- [ ] Spec reuses `playwright/.auth/user.json` via the existing `setup` project dependency — does not log in inline.
- [ ] Demo path walked end-to-end with only Tab / Shift+Tab / Enter / Space / Escape.
- [ ] Spec runs at both desktop and mobile viewports.
- [ ] Skip-to-main-content link is exercised and asserts focus lands on `<main id="main">`.
- [ ] Transaction sheet open / close assertion includes focus-into-sheet and focus-return-to-row.
- [ ] Spec runs green locally against a running webapp + local malai-api (per existing E2E conventions).
- [ ] No comments anywhere.

## Blocked by

- [01-appshell-bottom-tabs](./01-appshell-bottom-tabs.md)
- [03-money-balance-accounts-page](./03-money-balance-accounts-page.md)
- [04-account-detail-and-transaction-sheet](./04-account-detail-and-transaction-sheet.md)
- [05-stepflow-transfer-addpayee](./05-stepflow-transfer-addpayee.md)
- [06-move-money-pages-polish](./06-move-money-pages-polish.md)
- [07-auth-surface-redesign](./07-auth-surface-redesign.md)
- [09-profile-settings-and-mobile-switcher](./09-profile-settings-and-mobile-switcher.md)
