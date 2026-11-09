# `/settings` Profile card: full identity + `useUpdateUser` + display name refresh

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

Add the Profile card to the `/settings` page from slice 09. The card renders the **User**'s full identity (firstName, lastName, email, dateOfBirth) but allows editing only firstName and lastName via `PUT /api/v1/user`. Email and dateOfBirth are read-only with a "Contact support to change" affordance — the contract today restricts the request to firstName + lastName only (see `UpdateUserRequest` in the OpenAPI), and per CONTEXT.md these are the editable-via-self-service subset of the User profile.

**`useUpdateUser()`** at `src/auth/useUpdateUser.ts` — deep hook. Surface: `{ updateUser(input), isPending }`. Wraps `putApiV1UserMutation()`. On success, **writes the response into the user-query cache** via `queryClient.setQueryData(getApiV1UserQueryKey(), response.data)` so the AppShell's display name updates immediately without an extra round-trip. Also fires the Sonner toast _"Profile updated."_ On 4xx, falls through to the global generic toast (no inline disclosure).

**Profile card** inside `<SettingsPage>`:

- An `<h2>` "Profile" (no auto-focus — the page's `<h1>` already takes focus on mount).
- A `<form>` containing two text inputs (firstName, lastName) and two read-only `<dl>` rows (email, dateOfBirth).
- The two editable inputs use `useForm({ defaultValues: { firstName: user.firstName ?? '', lastName: user.lastName }, resolver: zodResolver(zUpdateUserRequest), shouldFocusError: true })`.
- The two read-only rows render the user's current email and dateOfBirth as text (date-fns `format` for the DoB, locale-appropriate UK format `d MMM yyyy`), each followed by helper text "Contact support to change". The helper text is rendered inside the same row, visually subordinate, with appropriate `aria-describedby` association if needed by axe.
- Save button "Save" (shows "Saving…" while pending). Disabled when the form is pristine or while the mutation is pending.

The user identity is read from `useUser()` (which is already the single source of truth per ADR-0001). After the mutation's `onSuccess` writes the new response to the query cache, the AppShell's display name re-renders automatically because the AppShell consumes the same query.

## Acceptance criteria

- [ ] `src/auth/useUpdateUser.ts` exports `useUpdateUser()` with `{ updateUser(input), isPending }`.
- [ ] On success, `useUpdateUser` writes the response to `getApiV1UserQueryKey()` via `queryClient.setQueryData(...)` and fires the _"Profile updated."_ toast.
- [ ] On 4xx, falls through to the global generic toast; no inline disclosure.
- [ ] `<SettingsPage>` renders a Profile card with `<h2>` "Profile", two editable text fields (firstName, lastName) and two read-only rows (email, dateOfBirth) each labelled "Contact support to change".
- [ ] DateOfBirth is rendered via date-fns `format(..., 'd MMM yyyy')`.
- [ ] Save button is disabled while pristine and while pending; shows "Saving…" during the mutation.
- [ ] After a successful save, the AppShell's top-right display name updates immediately without an extra `/api/v1/user` fetch (assert via network inspection in the E2E or via cache-state assertion in unit tests).
- [ ] No inline `setError('root', ...)` calls.
- [ ] Unit tests for `useUpdateUser`: happy path writes the new response into the user-query cache (assert `queryClient.getQueryData(userQueryKey)` reflects the response) and fires the success toast; 4xx falls through.
- [ ] Vitest UI test for the Profile card: read-only rows render correctly; Save is disabled when pristine; submitting fires the mutation.
- [ ] E2E test (light, idempotent on the shared user): with the shared signed-in `storageState`, visit `/settings`, change firstName to a known test value, save, assert the AppShell display name updates, then revert firstName to its original value, save, assert revert. This is the one new mutation safe to E2E because it's reversible on the shared user.
- [ ] `/settings` (with the Profile card visible) continues to pass the axe a11y spec; zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

- [09-appshell-dropdown-and-settings-stub.md](./09-appshell-dropdown-and-settings-stub.md)
