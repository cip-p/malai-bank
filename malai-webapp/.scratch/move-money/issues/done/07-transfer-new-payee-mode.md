# Transfer — New-payee mode (with Confirmation of Payee + save-as-payee)

Status: ready-for-agent

## Parent

[`.scratch/move-money/PRD.md`](../PRD.md)

## What to build

Activate the **New payee** tab on the Transfer form. Reuses the `CopaVerdictPanel` and `copaNextStep` built in slice 03; reuses the Transfer scaffolding (source dropdown, AmountInput, reference, two-step submit, idempotency-key handling, ADR-0008 error mapping) built in slice 05.

- Replace the _"Coming soon"_ placeholder content of the **New payee** tab with:
  - Manual destination entry: **Name** field labelled _"Name on the account"_ (max 140 chars), **Sort code** field (using `SortCodeInput` from slice 03), **Account number** field (using `AccountNumberInput` from slice 03).
  - **Amount** and **Reference** fields below (rendered by the shared transfer-form layout).
  - **Save as a payee** optional checkbox at the bottom of the form. Default off.
- Submit flow (only differs from slice 05's two-step path inside the _Continue_ → COPA branch):
  1. _Continue_ → mint COPA idempotency key, call `useConfirmationOfPayee` with `{ name, sortCode, accountNumber }`.
  2. Compute `copaNextStep(verdict, enteredName, onRecordName, 'transfer')`.
  3. `MATCH` → advance silently to the existing confirmation summary step.
  4. `CLOSE_MATCH` → render `CopaVerdictPanel` between entry and confirmation. _Use the on-record name_ substitutes the name in form state (and, **if Save as a payee is checked**, the persisted Payee will also carry the on-record name on the subsequent create POST). _Keep what I typed_ advances to confirmation with the entered name.
  5. `NO_MATCH` → render `CopaVerdictPanel`. _Edit details_ returns to the entry form preserving values. _Send anyway_ advances to confirmation with the entered name.
- _Send_ on the confirmation step:
  - If **Save as a payee** is checked: fire `useCreatePayee` first (with its own minted idempotency key), wait for success, then fire `useTransfer`. On payee-create failure, surface a generic toast and abort the transfer (the user can uncheck and retry without saving).
  - Otherwise: fire `useTransfer` directly.
- All error mapping inherits from slice 05 (insufficient-funds inline; everything else generic toast).

## Acceptance criteria

- [ ] The New-payee tab is enabled in `TransferPage`; the _"Coming soon"_ placeholder is removed.
- [ ] Name field labelled _"Name on the account"_ (verbatim per PRD); 140-char max enforced via the generated Zod schema.
- [ ] _Continue_ fires `POST /payees/confirmation-of-payee` with a freshly minted idempotency key; the key is **separate** from any subsequent transfer or payee-create key.
- [ ] `CopaVerdictPanel` renders between entry and confirmation when the verdict is `CLOSE_MATCH` or `NO_MATCH`; `MATCH` skips the panel and advances directly to the confirmation summary.
- [ ] `CLOSE_MATCH` _Use the on-record name_ substitutes the name in form state; subsequent confirmation summary shows the on-record name; subsequent `POST /payees` (when Save as a payee is checked) sends the on-record name as `name`.
- [ ] `NO_MATCH` _Edit details_ returns to the entry form with all values preserved (name, sort code, account number, amount, reference, save-as-payee checkbox state).
- [ ] `NO_MATCH` _Send anyway_ advances to confirmation; no override marker is sent on the transfer or payee-create POST (the backend audits separately).
- [ ] Save-as-payee enabled + COPA accepted → on _Send_, `POST /payees` fires first (with idempotency key); on success, `POST /transfers` fires (with its own idempotency key); on payee-create failure, the transfer is aborted and a generic toast appears.
- [ ] Save-as-payee disabled → only `POST /transfers` fires.
- [ ] Insufficient-funds inline error and generic-toast fallthrough inherit from slice 05 unchanged.
- [ ] Playwright E2E spec covers: (a) MATCH happy path (new-payee → COPA returns MATCH → confirm → send → success); (b) CLOSE*MATCH path with \_Use the on-record name* + Save-as-payee checked, asserting the created Payee carries the on-record name; (c) NO*MATCH path with \_Send anyway*, asserting the transfer proceeds.
- [ ] `tests/e2e/a11y.spec.ts` extended to scan the New-payee tab in entry, CLOSE_MATCH, and NO_MATCH states.
- [ ] No comments anywhere — including tests.

## Blocked by

- 03-payees-list-add-with-copa
- 05-transfer-my-accounts-mode
