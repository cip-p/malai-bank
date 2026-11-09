# Transfer — My-accounts mode (transfer scaffolding)

Status: ready-for-agent

## Parent

[`.scratch/move-money/PRD.md`](../PRD.md)

## What to build

The Transfer form scaffolding plus the **My accounts** destination tab functional end-to-end. The other two destination tabs (Saved payee / New payee) exist in this slice but are disabled with a small placeholder copy — they go live in slices 06 and 07. This is deliberately the bigger "scaffolding" slice; subsequent transfer slices are thin extensions.

- **`useTransfer`** mutation over `POST /api/v1/accounts/{accountId}/transfers`. Attaches the `Idempotency-Key` header (minted at submit time, never re-used after success). On success: invalidates the source account's `useAccount`, `useBalance`, and `useTransactions` queries; shows success toast (_"Transfer sent."_); resets the form.
- **`parseAmount`** pure helper: `(input: string) => { ok: true; minorUnits: number } | { ok: false; reason: 'empty' | 'invalid' | 'too-many-decimals' | 'below-minimum' | 'above-max' }`. Rejects negatives, scientific notation, comma-as-decimal, >2 decimals; accepts integer pounds, single decimal, two decimals; soft cap at `99_999_999` minor units (`£999,999.99`).
- **`AmountInput`** — single text input with a non-editable `£` adornment on the left, `inputMode="decimal"`. On blur, normalises display to two decimals (`12.4` → `12.40`). Validation via `parseAmount`. Inline RHF errors per ADR-0004.
- **`TransferPage`** replaces the slice-01 placeholder. Layout:
  - **Source-account dropdown** above the tabs, populated from `useAccounts()`. Pre-selects the source if `?from=<accountUid>` is present in the URL and matches an account.
  - **Destination-mode segmented control** (three tabs): **My accounts** (functional) · **Saved payee** (disabled, placeholder _"Coming soon"_) · **New payee** (disabled, placeholder _"Coming soon"_).
  - In **My accounts** mode: list the source-AccountHolder's other Accounts (sourced from the existing account-holder query, filtered to exclude the source). Empty state when only one Account: _"You only have one account — nothing to send to yourself."_ Selecting an own-Account pre-fills the hidden `toSortCode` + `toAccountNumber` state from that Account's identifiers.
  - **Amount** field (via `AmountInput`) and **Reference (optional)** single-line field with the 400-char counter behaviour.
- **Two-step submit**: entry form → confirmation step (plain text summary, _Back_ + _Send_ buttons) → submitted POST. _Back_ preserves form state. _Send_ is disabled while the mutation is pending.
- **Error mapping** per [ADR-0008](../../../docs/adr/0008-insufficient-funds-inline-error-carve-out.md): insufficient-funds 409 (matching the appropriate `MalaiCode`) renders an inline form-level error _"Not enough money in this account."_ leaving the form interactive; all other 4xx fall through to a generic toast. `401` is handled by the existing global handler.

## Acceptance criteria

- [ ] `useTransfer` mutation tested for: happy path (mutation succeeds + correct invalidation list), insufficient-funds 409 (no toast, error returned to caller for inline display), other-409 (generic toast), 403 (generic toast), 404 (generic toast).
- [ ] `parseAmount` unit tests cover: `''` (empty), `'0'`, `'0.00'`, `'-1'`, `'1e2'`, `'12,45'` (comma), `'12.456'` (too many decimals), `' 12.45 '`, `'£12.45'`, `'12'`, `'12.4'`, `'12.45'`, the soft-cap boundary, above the soft cap.
- [ ] `AmountInput` component test asserts: £ adornment renders; on-blur normalisation works (`12.4` → `12.40`); inline validation message renders for each `parseAmount` failure `reason`.
- [ ] `TransferPage` source dropdown is pre-selected when `?from=<accountUid>` matches an Account; falls back to the first Account when no `?from=` or no match.
- [ ] Destination-mode tabs render all three; only My accounts is enabled; the disabled tabs show _"Coming soon"_ copy when clicked / focused (per the existing disabled-tab a11y pattern).
- [ ] My-accounts mode: lists other Accounts; empty-state copy renders verbatim when the holder has a single Account; selecting an own-Account fills the hidden destination state.
- [ ] Two-step submit: entry → confirmation summary → POST. _Back_ preserves all form state. _Send_ disables while pending. Success path: form resets, invalidations fire, toast appears.
- [ ] Insufficient-funds 409 renders the inline form-level error verbatim (_"Not enough money in this account."_); the form stays interactive; the user can edit the amount and re-submit (which mints a new `Idempotency-Key`).
- [ ] Other 4xx errors surface as generic toasts per ADR-0007.
- [ ] Playwright E2E spec covers: own-to-own transfer happy path (signed-in user → `/move/send?from=<CURRENT-id>` → My accounts tab → pick SAVINGS → amount → confirm → send → success → toast + invalidation). Requires Sarah-Mitchell-style fixture for the second Account; if absent, the e2e covers the entry-and-confirm portion and asserts the empty-state copy.
- [ ] `tests/e2e/a11y.spec.ts` extended for `/move/send` (entry + confirmation states).
- [ ] No comments anywhere — including tests.

## Blocked by

- 01-scaffold-move-money-section
