# ProfilePage real content + SettingsPage redesign + mobile theme switcher

Status: ready-for-agent

## Parent

[`.scratch/design-and-themes/PRD.md`](../PRD.md)

## What to build

The mobile counterpart to the desktop header's user-menu + theme switcher, and the SettingsPage redesigned in the new system. After this slice lands, a mobile **User** has every Profile-tab affordance — Settings, Theme switcher, Mode toggle, Sign out — reachable from one tab; a desktop User sees the same controls grouped into a redesigned SettingsPage with Appearance now first-class.

- **ProfilePage** (mobile-only destination, replaces the stub from [01](./01-appshell-bottom-tabs.md)):
  - **Identity block** at the top: name + email via the existing `displayNameOf` helper.
  - **Appearance section**: the `ThemeSwitcher` component from [08](./08-theme-catalogue-switcher.md), surfaced inline rather than as a header dropdown — list the three Themes with swatches and active-checkmarks, tap to apply. Also a Mode toggle (Light / Dark / System) wired to `next-themes`.
  - **Settings link**: navigates to `/settings`.
  - **Sign out** action: invokes the existing `useSignout` hook.
  - Desktop visiting `/profile` redirects to `/settings` so there is one canonical home per breakpoint.
- **SettingsPage** redesigned into three labelled sections:
  - **Profile**: the existing editable subset (firstName, lastName) and immutable-via-self-service subset (email, dateOfBirth) per CONTEXT.md, in the new design system. Existing form behaviour (Zod from `openapi:gen`, focus-on-error, error mapping) unchanged.
  - **Security**: stub section heading + "_Coming soon._" body copy for now. Reserves the space without inventing functionality.
  - **Appearance**: the same Theme + Mode controls as on the mobile ProfilePage. On desktop the user can pick a theme from here as an alternative to the header switcher; both surfaces drive the same `useTheme` store.
- Both ProfilePage and SettingsPage inherit Inter + refreshed tokens from [02](./02-typography-and-malai-refresh.md).

## Acceptance criteria

- [ ] Mobile `/profile` renders the identity block, the inline Appearance section (Theme + Mode), a Settings link, and a Sign out action.
- [ ] Desktop visiting `/profile` redirects to `/settings`.
- [ ] Mobile ProfilePage's Theme controls drive the same `useTheme` store as the desktop header switcher — switching from one surface is reflected in the other.
- [ ] Mode toggle on ProfilePage and SettingsPage wires to `next-themes`; Light / Dark / System options behave correctly.
- [ ] SettingsPage renders three sections: Profile (existing User-profile editable fields preserved verbatim), Security (stub), Appearance (Theme + Mode controls).
- [ ] Existing SettingsPage form behaviour (validation, error mapping, success state) unchanged — existing tests pass.
- [ ] New ProfilePage component test covers: identity block render, Theme switcher inline render, Mode toggle render, Sign out invocation.
- [ ] axe-core a11y spec extended over ProfilePage and the redesigned SettingsPage without violations.
- [ ] No comments anywhere.

## Blocked by

- [01-appshell-bottom-tabs](./01-appshell-bottom-tabs.md) (Profile tab + the stub route this slice fills in)
- [08-theme-catalogue-switcher](./08-theme-catalogue-switcher.md) (`ThemeSwitcher` component is reused; `useTheme` store is shared)
