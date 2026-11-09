# Payees: list + Add (with Confirmation of Payee)

Status: ready-for-agent

## Parent

[`.scratch/move-money/PRD.md`](../PRD.md)

## What to build

The Payees list screen and the dedicated Add-payee route, with **Confirmation of Payee** running on submit and the three-verdict UX surfaced via a reusable `CopaVerdictPanel` component (also used by slice 07 on the Transfer New-payee tab).

- **`usePayees`** hook over `GET /api/v1/payees`. Mirrors `useAccounts` pattern. Sends the `MAL-Account-Holder-Id` header.
- **`useCreatePayee`** mutation over `POST /api/v1/payees`. Attaches the `Idempotency-Key` header. On success: invalidates the payees query, shows success toast (_"Payee saved."_), navigates back to `/move/payees`.
- **`useConfirmationOfPayee`** mutation over `POST /api/v1/payees/confirmation-of-payee`. Attaches its own `Idempotency-Key`, independent of the subsequent create's key.
- **`mintIdempotencyKey`** pure helper: `() => string` returning a UUID v4. The single seam for the contract.
- **`copaNextStep`** pure helper: `(verdict, enteredName, onRecordName?, intent: 'transfer' | 'save-payee') => CopaUiState`. Encodes the three-verdict UX decision tree. The discriminated-union `CopaUiState` is the contract `CopaVerdictPanel` consumes.
- **`CopaVerdictPanel`** deep component — props: `(state, enteredName, onRecordName?, onAccept, onUseOnRecord?, onEdit, onSendAnyway?)`. Renders:
  - `MATCH` → silent (renders nothing; intended to be skipped past).
  - `CLOSE_MATCH` → message _"On record as **{onRecordName}** — you entered **{enteredName}**"_ + two buttons _Use the on-record name_ (fires `onUseOnRecord`) and _Keep what I typed and continue_ (fires `onAccept`).
  - `NO_MATCH` → warning _"We couldn't verify this account. Please double-check the details."_ + two buttons _Edit details_ (fires `onEdit`) and copy-varies-by-intent (`Save anyway` for `save-payee`, `Send anyway` for `transfer`) (fires `onSendAnyway`).
- **`formatSortCodeInput`** pure helper. `'123456'` → `'12-34-56'`; handles partial input, paste with spaces, deletion across dash boundaries.
- **`SortCodeInput`** component using `formatSortCodeInput` on change. **`AccountNumberInput`** component restricting input to 8 digits, stripping non-digits.
- **`PayeeRow`** — name primary, nickname secondary (if present), sort code + account number (formatted, masked-as-displayed not the full digits) on the right. Delete affordance is **not** in this slice — comes in slice 04. For now, no row action.
- **`PayeesPage`** replaces the slice-01 placeholder. Loading skeleton. List sorted alphabetically by `nickname ?? name`. Empty state copy per PRD with an _Add payee_ primary CTA linking to `/move/payees/new`. Header has _Add payee_ button regardless of list emptiness.
- **`AddPayeePage`** at `/move/payees/new`. Fields: name (label _"Name on the account"_), sort code, account number, optional nickname. RHF + Zod (generated `PayeeRequest` schema extended with the sort-code dash pattern). Submit flow:
  1. Mint COPA idempotency key, call `useConfirmationOfPayee`.
  2. Compute next state via `copaNextStep(verdict, enteredName, onRecordName, 'save-payee')`.
  3. `MATCH` → mint create idempotency key, fire `useCreatePayee` immediately.
  4. `CLOSE_MATCH` → render `CopaVerdictPanel`; on _Use the on-record name_ substitute the name in form state then fire create with the on-record name; on _Keep what I typed_ fire create with the entered name.
  5. `NO_MATCH` → render `CopaVerdictPanel`; on _Edit details_ return to the form preserving values; on _Save anyway_ fire create with the entered name.
- Error mapping per ADR-0008: generic toast on 4xx fallthrough. RHF field-level errors for `400` validation.

## Acceptance criteria

- [ ] `usePayees`, `useCreatePayee`, `useConfirmationOfPayee` hooks follow the existing `useAccounts` pattern with unit tests mirroring `useAccounts.test.tsx`.
- [ ] `mintIdempotencyKey` is a uuid v4 generator with a unit test asserting uniqueness across many calls.
- [ ] `formatSortCodeInput` unit tests cover: empty, `'1'`, `'12'`, `'123'`, `'1234'`, `'12345'`, `'123456'`, pre-dashed input, paste with spaces, deletion across dash boundaries.
- [ ] `copaNextStep` unit tests cover all three verdicts crossed with both intents (`transfer` / `save-payee`); CLOSE_MATCH state carries the on-record name; tests verify the discriminated-union shape, not internal layout.
- [ ] `CopaVerdictPanel` component test covers each verdict branch and asserts the right callbacks fire on the right buttons; copy for the _Save anyway_ / _Send anyway_ button matches the `intent` prop.
- [ ] `PayeesPage` renders the alphabetical list, the explanatory empty state with _Add payee_ CTA, and a header _Add payee_ button. Loading skeleton present.
- [ ] `AddPayeePage` form-validation behaviour matches the generated Zod schema + sort-code pattern; submit runs COPA first; the three-verdict UX flows through `CopaVerdictPanel`.
- [ ] On successful save, the user is navigated back to `/move/payees`, the payees query is invalidated, and a success toast (_"Payee saved."_) appears.
- [ ] Generic toast on 4xx fallthrough; no inline disclosure of "duplicate payee" or other holder-state.
- [ ] Playwright E2E spec covers: signed-in user opens `/move/payees`, clicks Add payee, fills the form, submits, sees MATCH path → payee appears in the list. Second E2E scenario forces a CLOSE*MATCH and exercises the \_Use the on-record name* path.
- [ ] `tests/e2e/a11y.spec.ts` extended for `/move/payees` and `/move/payees/new`.
- [ ] No comments anywhere — including tests.

## Blocked by

- 01-scaffold-move-money-section
