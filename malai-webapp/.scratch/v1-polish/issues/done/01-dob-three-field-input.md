# Issue 01: DOB three-field input on Registration

Status: ready-for-agent

## Parent

[v1-polish PRD](../PRD.md)

## What to build

Replace the native `<input type="date">` on the Registration "About you" step with a three-field Day / Month / Year numeric entry, modelled on the GOV.UK pattern. The combined value is `YYYY-MM-DD` and is fed to React Hook Form so the existing Zod schema and the backend contract are unchanged.

The trio is implemented as a new presentational component (`DateOfBirthInput` or similar) so it's reusable wherever else a date-of-birth-shaped input is needed later. Its interface is intentionally string-in / string-out — the canonical `YYYY-MM-DD` if all three sub-fields produce a valid past date, otherwise `""`. A single component-level `error?` slot surfaces a humane message.

Validation rule (priority-ordered, one message at a time):

1. Any sub-field empty → "Enter your date of birth"
2. Any sub-field non-numeric or out of range → "Enter a real date of birth"
3. Combined (year, month, day) is not a real calendar date (handles short months and leap years) → "Enter a real date of birth"
4. Combined date is today or in the future → "Date of birth must be in the past"

Each sub-field uses `inputmode="numeric"` so the phone keyboard shows the number pad. The Day / Month fields are narrow (two digits); the Year field is wider (four digits). The three are wrapped in a `<fieldset>` with a `<legend>` of "Date of birth" for screen-reader grouping; per-field labels still exist (e.g. "Day", "Month", "Year") for visible UI and SR announcement.

Going back from the Address step preserves the DOB fields (the existing React Hook Form state covers this — confirm it still works after the swap).

No backend changes. The submitted payload is still `RegisterRequest.dateOfBirth: "YYYY-MM-DD"`.

## Acceptance criteria

- [ ] A new `DateOfBirthInput` (or equivalently-named) component lives in the components folder, with string-in / string-out interface and Vitest unit coverage across the eight or so case rows: empty, partial, non-numeric, out-of-range, non-real-date, leap-year (Feb-29 valid 2020 vs invalid 2021), future date, valid past date.
- [ ] `RegisterAboutPage` uses the new component in place of `<input type="date">`. The "Invalid ISO date" message can no longer be produced for any user-typeable input.
- [ ] Each sub-field uses `inputmode="numeric"` and accepts only digits.
- [ ] The trio is wrapped in a `<fieldset>` + `<legend>="Date of birth"` for SR grouping.
- [ ] Validation messages match the priority-ordered rule above; never "Invalid ISO date".
- [ ] On submit, `RegisterRequest.dateOfBirth` is sent as `YYYY-MM-DD` and the existing Zod schema (`z.iso.date()`) does not reject it.
- [ ] Navigating back from the Address step and forward again preserves the entered DOB.
- [ ] `RegisterAboutPage.test.tsx` is updated to drive the new component, with assertions on validation messaging and on the submitted DOB shape.
- [ ] `npm test` passes; `npm run lint` passes; `npm run prettier` passes.

## Blocked by

None - can start immediately.
