# AppShell top-right `DropdownMenu` (Settings / Sign out) + `/settings` stub page

Status: ready-for-agent

## Parent

[../PRD.md](../PRD.md) — Finishing the auth surface: register, confirm, forgot, change, profile

## What to build

Refactor the AppShell's top-right area from inline `{displayName} [Sign out]` to a `DropdownMenu` trigger that opens a small menu with two items: _Settings_ and _Sign out_. Add a stub `/settings` route so the menu's Settings item navigates to a real page (content lands in slices 10 and 11).

**AppShell change** (`src/components/AppShell.tsx`):

- Replace the inline `<span>{displayName}</span><Button>Sign out</Button>` cluster with a `<DropdownMenu>` from `radix-ui` (the umbrella `radix-ui` package, matching how `Collapsible` is already imported in this file — do **not** introduce `@radix-ui/react-dropdown-menu` or a shadcn wrapper).
- Trigger: `<DropdownMenu.Trigger>` rendering `<button>{displayName} <ChevronDown aria-hidden="true" /></button>`. The button is keyboard-focusable and styled with theme tokens.
- Menu items, in this order:
  1. _Settings_ — `<DropdownMenu.Item asChild><Link to="/settings">Settings</Link></DropdownMenu.Item>`.
  2. _Sign out_ — `<DropdownMenu.Item onSelect={() => signout()}>Sign out</DropdownMenu.Item>`. Disabled while `useSignout().isPending` is true.
- Open/close transition (radix's built-in animation) respects `motion-safe` via Tailwind's variant.
- Keyboard navigation (arrow keys, Enter, Escape, Tab to dismiss) is provided by radix's primitive — no manual wiring.
- The `data-testid="display-name"` element from the existing AppShell test moves onto the trigger button (or its label) so existing assertions continue to find it.

**`<SettingsPage>` stub** at `src/pages/SettingsPage.tsx`:

- `<h1>` "Account settings" focused on mount via `useFocusOnMount`.
- Renders within `<Authenticated>` (so the AppShell chrome wraps it).
- No card content yet — placeholder text _"Settings coming soon"_ is fine for this slice. Slices 10 and 11 add the Profile and Security cards respectively.

**Router change**:

- Declare `<Route path="/settings" element={<SettingsPage />} />` under the existing `<Authenticated>` parent.

## Acceptance criteria

- [ ] `AppShell.tsx` imports `DropdownMenu` from `radix-ui` (the same umbrella package that already provides `Collapsible` in this file).
- [ ] The top-right of the AppShell renders a single `<DropdownMenu.Trigger>` button containing the display name and a chevron icon; the inline `[Sign out]` button is gone.
- [ ] The dropdown menu contains exactly two items in order: _Settings_ (navigates to `/settings`) and _Sign out_ (calls `useSignout().signout()`).
- [ ] _Sign out_ item is disabled while `useSignout().isPending` is true.
- [ ] The `data-testid="display-name"` selector continues to find the user's display name (existing AppShell tests pass with at most a selector update if absolutely necessary).
- [ ] Keyboard navigation works: Tab focuses the trigger; Enter / Space opens the menu; arrow keys move between items; Escape closes; Enter activates the focused item.
- [ ] The dropdown's open/close transition respects `prefers-reduced-motion`.
- [ ] `/settings` route declared under `<Authenticated>`; renders `<SettingsPage>` stub with `<h1>` "Account settings" focused on mount.
- [ ] Vitest test for AppShell covers: dropdown trigger renders the display name; opening the menu reveals Settings and Sign out items; Sign out item invokes the signout flow.
- [ ] E2E test: with the shared signed-in `storageState`, visit `/`, assert the dropdown trigger is keyboard-reachable, open it, assert _Settings_ and _Sign out_ items are present, click _Settings_, assert navigation to `/settings`.
- [ ] `/settings` added to the existing axe a11y spec (using `storageState`); zero violations on `malai` theme (light + dark).
- [ ] `npm run lint`, `npm test`, `npm run test:e2e`, `npm run a11y`, `npm run prettier`, `npm run build` all pass.

## Blocked by

None — can start immediately.
