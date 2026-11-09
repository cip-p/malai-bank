# Money + Balance primitives + AccountsPage hero redesign

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The first real page-level redesign. After this slice lands, a signed-in **User** opening `/` sees a Starling/Chase-class home screen: greeting, large total-balance hero with a count-up on first paint, three quick-action icons, and a card stack of their **Accounts**. The two money primitives introduced here are reused by every later slice.

- **`<Money>`** — props `{ minorUnits: number, currency?: 'GBP', signed?: 'always' | 'negative' | 'never' }`. Pure formatting via `Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' })`. Applies `tabular-nums`. Direction-coloured when `signed: 'always'`: positive uses a positive-tone token, negative uses a negative-tone token. Zero renders as `£0.00` without sign. Replaces all ad-hoc money rendering across the app touched in this slice.
- **`<Balance>`** — props `{ minorUnits: number }`. Renders a `<Money>` but animates the displayed integer from `0` to `minorUnits` over ~600ms on mount. `motion-safe` gated — under `prefers-reduced-motion: reduce` the final value renders immediately. Accessibility name is always the final value.
- **AccountsPage** redesigned:
  - **Greeting**: "_Good morning, {firstName}_" / "_Good afternoon, …_" / "_Good evening, …_". Falls back to email via the existing `displayNameOf` helper.
  - **Total balance hero**: large display type, `<Balance>` over the sum of `balanceMinorUnits` for every Account of the **Active AccountHolder**.
  - **Quick actions row**: three icon-label buttons in a horizontal row — **Send money** (→ `/move/send`), **Manage payees** (→ `/move/payees`), **Settings** (→ `/settings` on desktop, → `/profile` on mobile).
  - **Account card stack**: one card per Account. Each card shows the Account's display name, masked sort code + account number, current balance (`<Money>`), and the most recent Transaction as a one-line preview (counterparty + signed amount). Tapping a card navigates to `/accounts/:accountUid`.
  - Empty / single-Account states render with no fallback prose — one card, no special UI.

## Acceptance criteria

- [ ] `<Money>` Vitest unit tests cover: positive / negative / zero formatting; signed display rules; en-GB locale (`£` + thousands separator + two decimals); tabular-nums applied.
- [ ] `<Balance>` Vitest unit tests cover: settles on the final value; `prefers-reduced-motion: reduce` (mocked `matchMedia`) renders the final value immediately with no animation; accessible name reflects the final value.
- [ ] Greeting picks one of morning / afternoon / evening based on the User's local time and uses `firstName` with the existing `displayNameOf` fallback.
- [ ] Total balance is the sum of every Account's `balanceMinorUnits` for the Active AccountHolder; animates from `£0.00` to the total under motion-safe; renders the total immediately under reduce.
- [ ] Three quick-action items render with the correct destinations; on mobile the Settings item routes to `/profile`, on desktop to `/settings`.
- [ ] Each Account card renders display name, masked identifiers, current balance, and a most-recent-Transaction preview line; tap navigates to `/accounts/:accountUid`.
- [ ] AccountsPage component test updated to assert visible greeting, hero figure, three quick actions with correct hrefs at both breakpoints, and the rendered Account cards.
- [ ] All bespoke motion respects `prefers-reduced-motion: reduce` per ADR-0006 #3.
- [ ] axe-core a11y spec extended over the redesigned AccountsPage without violations.
- [ ] No comments anywhere.

## Blocked by

- [01-appshell-bottom-tabs](./01-appshell-bottom-tabs.md) (mobile Settings quick action targets `/profile`)
- [02-typography-and-malai-refresh](./02-typography-and-malai-refresh.md) (Inter + refreshed tokens are inherited)
