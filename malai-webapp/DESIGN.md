---
name: Malai
description: A calm, well-run UK high-street bank on warm paper, with a back office that is visibly the same institution with the branding off.
colors:
  espresso-rust: "oklch(0.46 0.13 40)"
  espresso-rust-dark: "oklch(0.74 0.14 50)"
  deep-harbour-navy: "oklch(0.36 0.13 255)"
  deep-harbour-navy-dark: "oklch(0.74 0.14 235)"
  linden-forest: "oklch(0.38 0.11 150)"
  linden-forest-dark: "oklch(0.74 0.13 150)"
  cream-paper: "oklch(0.985 0.012 80)"
  cream-paper-dark: "oklch(0.18 0.018 50)"
  espresso-ink: "oklch(0.205 0.028 50)"
  espresso-ink-dark: "oklch(0.96 0.012 80)"
  card-white: "oklch(1 0 0)"
  card-dark: "oklch(0.22 0.018 50)"
  warm-linen: "oklch(0.945 0.015 75)"
  warm-linen-dark: "oklch(0.26 0.018 50)"
  soft-cocoa: "oklch(0.42 0.025 55)"
  soft-cocoa-dark: "oklch(0.82 0.018 75)"
  apricot-wash: "oklch(0.92 0.045 65)"
  apricot-wash-dark: "oklch(0.32 0.035 55)"
  hairline: "oklch(0.88 0.018 75)"
  hairline-dark: "oklch(0.3 0.018 50)"
  control-stroke: "oklch(0.62 0.018 75)"
  control-stroke-dark: "oklch(0.54 0.018 50)"
  paper-on-ink: "oklch(0.99 0.01 80)"
  brick-refusal: "oklch(0.48 0.18 25)"
  brick-refusal-dark: "oklch(0.72 0.17 25)"
  credit-green: "oklch(0.45 0.13 145)"
  credit-green-dark: "oklch(0.78 0.16 145)"
typography:
  display:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: "-0.025em"
    fontVariation: "tabular-nums"
  headline:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.875rem"
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  title:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.33
    letterSpacing: "-0.025em"
  figure:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.4
    letterSpacing: "normal"
    fontVariation: "tabular-nums"
  subtitle:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 600
    lineHeight: 1.55
    letterSpacing: "normal"
  body:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 500
    lineHeight: 1.5
    letterSpacing: "normal"
    fontFeature: "cv11, ss01, ss03"
  supporting:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.43
    letterSpacing: "normal"
    fontFeature: "cv11, ss01, ss03"
  label:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.33
    letterSpacing: "normal"
  eyebrow:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.33
    letterSpacing: "0.05em"
  caption:
    fontFamily: "Inter Variable, Inter Fallback, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.625rem"
    fontWeight: 400
    lineHeight: 1.4
    letterSpacing: "normal"
  mono:
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: 1.33
    letterSpacing: "normal"
rounded:
  sm: "calc(var(--radius) - 4px)"
  md: "calc(var(--radius) - 2px)"
  lg: "var(--radius)"
  xl: "calc(var(--radius) + 4px)"
  hero: "calc(var(--radius) + 6px)"
  full: "9999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
  2xl: "40px"
components:
  button-primary:
    backgroundColor: "{colors.espresso-rust}"
    textColor: "{colors.paper-on-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  button-primary-hover:
    backgroundColor: "{colors.espresso-rust}"
  button-outline:
    backgroundColor: "{colors.cream-paper}"
    textColor: "{colors.espresso-ink}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  button-outline-hover:
    backgroundColor: "{colors.warm-linen}"
  button-ghost:
    textColor: "{colors.espresso-ink}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  button-ghost-hover:
    backgroundColor: "{colors.warm-linen}"
  button-destructive:
    textColor: "{colors.brick-refusal}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "0 10px"
  button-form:
    backgroundColor: "{colors.espresso-rust}"
    textColor: "{colors.paper-on-ink}"
    rounded: "{rounded.lg}"
    height: "44px"
    width: "100%"
  input:
    textColor: "{colors.espresso-ink}"
    rounded: "{rounded.lg}"
    height: "32px"
    padding: "4px 10px"
  input-form:
    textColor: "{colors.espresso-ink}"
    rounded: "{rounded.lg}"
    height: "44px"
    padding: "4px 10px"
  card:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.espresso-ink}"
    rounded: "{rounded.xl}"
    padding: "16px"
  card-hero:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.espresso-ink}"
    rounded: "{rounded.xl}"
    padding: "24px"
  metric-tile:
    backgroundColor: "{colors.card-white}"
    textColor: "{colors.espresso-ink}"
    rounded: "{rounded.hero}"
    padding: "24px"
  chip-category:
    backgroundColor: "{colors.warm-linen}"
    textColor: "{colors.soft-cocoa}"
    rounded: "{rounded.full}"
    padding: "2px 8px"
  tag-status:
    textColor: "{colors.espresso-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "2px 6px"
  nav-link-desktop:
    textColor: "{colors.soft-cocoa}"
    typography: "{typography.body}"
    height: "40px"
    padding: "0 12px"
  nav-link-desktop-active:
    textColor: "{colors.espresso-ink}"
  tab-mobile:
    textColor: "{colors.soft-cocoa}"
    typography: "{typography.label}"
    padding: "8px 8px 4px"
  tab-mobile-active:
    textColor: "{colors.espresso-rust}"
  card-art:
    backgroundColor: "{colors.espresso-rust}"
    textColor: "{colors.paper-on-ink}"
    rounded: "{rounded.hero}"
    padding: "20px"
  icon-well:
    backgroundColor: "{colors.warm-linen}"
    textColor: "{colors.soft-cocoa}"
    rounded: "{rounded.full}"
    size: "40px"
---

# Design System: Malai

## Overview

**Creative North Star: "The Branch Counter"**

Malai looks like a calm, well-run high-street bank. The canvas is warm paper, the ink is espresso, and there is exactly one brand colour, used the way a good bank uses its brand: on the sign above the door, on the card in your hand, and on the button that moves money. Everything else is tone. Rules are hairlines, surfaces sit flat on the page, figures line up in tabular columns. Nothing bounces, nothing glows, and nothing tries to entertain; the system earns trust by being exact.

The same institution runs the back office. Ops and Exec pin the default malai token set, swap the customer's bottom tabs for a wider container and a text table, and add monospace identifiers and square-cornered status tags. That is the whole difference. Walk from `/` into `/ops` and the type, spacing, borders and controls are the ones you just left; only the density and the vocabulary change. The theme switcher (halcyon, linden) reskins the customer side alone, so the constancy of the back office is a visible fact rather than a claim.

The look is deliberately not a neon challenger bank (no gradients-as-decoration, no glass, no mascots, no confetti), not a developer dashboard (no dark-by-default admin chrome, no monospace outside identifiers, no sidebars on the customer side), and not a legacy bank portal (no navy-and-grey tables, no tiny type, no dated iconography).

**Key Characteristics:**

- Warm, calm, exact: cream paper, espresso ink, one accent, tabular figures everywhere money or ids appear.
- Flat by default: hairline borders and tone carry structure; a real shadow means the element has left the page.
- One type family (Inter Variable) at one small body size (14px), with weight and tracking doing the hierarchy work.
- Three fictional brands as skins: palette and corner radius change, nothing else does.
- Honesty as a visual rule: an em-dash for an absent number, a dashed panel for an empty or failed state, a refusal rendered as a fact rather than an error.

## Colors

A single warm accent on a cream canvas, with every supporting tone mixed from the same hue as the paper rather than introduced as a second colour.

### Primary

- **Espresso Rust** (`oklch(0.46 0.13 40)`, dark mode `oklch(0.74 0.14 50)`): the one brand colour. Wordmark, primary button fill, active bottom tab, link text, the focus ring, the card art gradient, and the giant blurred halo behind the paused page. It is the `theme-color` of the PWA (`#913814`).
- **Deep Harbour Navy** (`oklch(0.36 0.13 255)`, dark `oklch(0.74 0.14 235)`): the halcyon skin's primary, same roles. Fintech-challenger archetype, radius 8px.
- **Linden Forest** (`oklch(0.38 0.11 150)`, dark `oklch(0.74 0.13 150)`): the linden skin's primary, same roles. Incumbent-heritage archetype, radius 12px.

### Neutral

- **Cream Paper** (`oklch(0.985 0.012 80)`, dark `oklch(0.18 0.018 50)`): the page. Also the fill of outline buttons and the header and nav bars, so chrome reads as part of the page, not a bar on top of it.
- **Espresso Ink** (`oklch(0.205 0.028 50)`, dark `oklch(0.96 0.012 80)`): all primary text and figures.
- **Card White** (`oklch(1 0 0)`, dark `oklch(0.22 0.018 50)`): account cards, hero cards, metric tiles, popovers. A step lighter than the paper in light mode, a step lighter than the page in dark mode.
- **Warm Linen** (`oklch(0.945 0.015 75)`, dark `oklch(0.26 0.018 50)`): the muted and secondary fill. Icon wells, category chips, hover fill on ghost and outline buttons, menu highlight. At 40% opacity it is every skeleton and every row hover.
- **Soft Cocoa** (`oklch(0.42 0.025 55)`, dark `oklch(0.82 0.018 75)`): secondary text. The most used colour token in the app (identifiers, descriptions, table headers, inactive nav, timestamps, the honest em-dash).
- **Apricot Wash** (`oklch(0.92 0.045 65)`, dark `oklch(0.32 0.035 55)`): the accent tint. The active tab's pill behind its icon, initial avatars, the first-run hint banner. The only tinted surface with a hint of the brand hue.
- **Hairline** (`oklch(0.88 0.018 75)`, dark `oklch(0.3 0.018 50)`): every border and divider, and the stroke on buttons, tags and cards. One weight (1px), one colour.
- **Control Stroke** (`oklch(0.62 0.018 75)`, dark `oklch(0.54 0.018 50)`): the 1px stroke on anything that takes typed input: text fields, textareas, native selects, OTP slots and the segmented destination control. Mixed from the paper hue like the Hairline, but held at 3:1 against paper, card and linen in both Modes so a field is findable without a fill. At 30% it is the dark-mode field fill; at 50% the disabled fill.
- **Paper on Ink** (`oklch(0.99 0.01 80)`): text on a primary fill.

### Semantic

- **Credit Green** (`oklch(0.45 0.13 145)`, dark `oklch(0.78 0.16 145)`): money arriving, and the success tick. Only ever paired with a leading `+`.
- **Brick Refusal** (`oklch(0.48 0.18 25)`, dark `oklch(0.72 0.17 25)`): destructive actions, declined card badges, form errors, timed-out or errored decision tags, closed-as-reported case tags. It is both `destructive` and `negative` in the token set; the two are the same value in every theme.
- **No chart palette.** The spending breakdown draws its bars in the one brand colour and identifies categories by icon and label, so the app carries no categorical hue set at all. The One Ink Rule holds without exception; the earlier eight-hue donut palette was removed because a ring of eight slices cannot be made distinguishable for colour-blind readers by any palette, and the legend that made it readable was already the better chart.

### Named Rules

**The One Ink Rule.** Each theme has exactly one hue that is not the paper's. It goes on the wordmark, the primary button, the active tab, links and the focus ring, and on nothing decorative. Secondary, muted, accent and border are all mixed from the canvas hue.

**The Money Direction Rule.** Credits are Credit Green with a `+`. Debits are plain Espresso Ink with a `−`, never red. Brick Refusal marks a refusal or a destructive action, never a debit.

**The Skins Rule.** A theme changes the palette block and `--radius`. It does not change type, spacing, shadows, iconography or layout. The Ops and Exec surfaces pin the malai block and show no switcher.

## Typography

**Display Font:** Inter Variable (with a metric-matched Arial fallback, then ui-sans-serif)
**Body Font:** Inter Variable (same)
**Label/Mono Font:** ui-monospace, for record identifiers on the Ops surface only

**Character:** One variable sans, set small and tight, with `cv11`, `ss01` and `ss03` stylistic sets on for the open single-storey a and straighter terminals. Hierarchy comes from weight (400 → 500 → 600) and negative tracking on anything 24px and up, never from a second family. Every figure that could be compared to another is tabular.

### Hierarchy

- **Display** (600, 36px, tracking −0.025em, tabular): the available balance on the account hero, the four executive metric tiles, the amount on a transaction sheet at 30px. Figures only; a heading never uses it.
- **Headline** (600, 30px, tracking −0.025em): the h1 on landing pages (Accounts, Profile, the Exec dashboard, the auth screens, the paused page on desktop).
- **Title** (600, 24px, tracking −0.025em): the default page h1 and the outcome screens ("Transfer sent", "We didn't make this payment"). Every page has exactly one, focusable, receiving focus on route change.
- **Subtitle** (600, 18px, no tracking): dialog and sheet titles, and the in-page section heading (the most used heading in the app). The mobile top-bar page title is 14px/500.
- **Card figure** (600, 20px, tabular): the balance on an account card and any figure that leads a list card.
- **Body** (500, 16px): the primary line of a list row (merchant, payee, account name), and form controls, which stay at 16px so mobile browsers do not zoom. This is the browser base; nothing sets it smaller.
- **Supporting** (400, 14px): everything around the primary line. Descriptions, table cells, secondary row lines, menu items, buttons (500), the paused page's lede at 16px.
- **Label** (500, 12px): tags, badges, timestamps, table sub-lines, the mobile tab captions, chip counts.
- **Eyebrow** (500, 12px, tracking 0.05em, uppercase): step indicators ("Step 2 of 3"), sticky date-group labels, the cardholder name on card art, the Ops / Exec surface badge in the header.
- **Caption** (400, 10px, uppercase): the "Exp" and "CVV" captions on card art only, as printed cards do.
- **Mono** (400, 12px): decision and case identifiers, correlation ids, content hashes on the Ops surface.

### Named Rules

**The Tabular Rule.** Money, sort codes, account numbers, counts, timestamps and identifiers are always `tabular-nums`. Columns of figures must align without effort.

**The Two Sizes Rule.** A row's primary line is 16px and everything supporting it is 14px. Do not drift supporting text up to 16px for comfort; the density is the bank. Do not set primaries or inputs below 16px; that is the mobile floor.

**The One H1 Rule.** One h1 per page, 24px by default, 30px on a landing page, `tabIndex={-1}` with no visible outline, and it receives focus on arrival.

## Layout

The customer app is a single centred column, 64rem wide (`max-w-5xl`) with 24px side padding, under a 48px header. Below 768px the header hides, a sticky 48px top bar carries the wordmark and the current page title, and a fixed bottom tab bar (four tabs, safe-area aware, 72px of scroll clearance) takes over navigation. At 768px and above a horizontal text nav sits under the header and the bottom bar disappears. Auth screens use a 28rem column (`max-w-md`) with the wordmark centred above a centred title.

The staff surfaces widen the column to 72rem (`max-w-6xl`), keep the 48px header (56px on Exec), replace the customer nav with a three-item Ops nav (Worklist, Cases, Chain), and never show a bottom bar. Exec lays its four metric tiles on a responsive grid (1 → 2 → 4 columns at 640px and 1280px).

Rhythm is a 4px scale used sparingly: 8px between siblings, 12px inside compact rows, 16px card padding, 24px hero and page padding, 24px between page sections (`mt-6`), 40px of breathing room around a centred outcome screen. Cards on the Accounts page sit in a one-column grid that becomes two at 768px. Quick actions are a three-column grid of small cards. Content that is meant to be read (descriptions, the Access page) is capped at `max-w-prose` or `max-w-sm`.

Breakpoints are Tailwind's defaults; the app uses `md` (768px) almost exclusively, `sm` (640px) for a handful of grid and padding steps, `lg` and `xl` rarely.

## Elevation & Depth

Flat by default, lifted only when floating. Surfaces at rest are defined by a 1px Hairline border and a tonal step (paper → card white). Cards carry a whisper shadow as a paper cue, not as depth, and gain one more step on hover to acknowledge the pointer. A real shadow means the element has left the page: a dropdown menu, an alert dialog, the transaction sheet, or the bank card itself, which is the one object in the app that is meant to look like an object.

### Shadow Vocabulary

- **Whisper** (`box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.05)`): every card and tile at rest. Barely there; the border does the work.
- **Acknowledge** (`box-shadow: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)`): card hover, together with the border warming to a 40% ring colour.
- **Held** (`box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)`): dropdown menus and the card art. Something you could pick up.
- **Floating** (`box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)`): dialogs and sheets, over a black scrim at 50% (70% in dark mode).

### Named Rules

**The Off-The-Page Rule.** A Held or Floating shadow is reserved for elements that overlay other content. Nothing in the document flow gets more than Whisper at rest or Acknowledge on hover.

**The Dashed Panel Rule.** Empty states and request failures are a dashed Hairline border on a hero-radius panel with centred text: a 16px/500 title, an optional Soft Cocoa line beneath, and at most one action. No colour, no shadow. Request failures render through `StatusPanel` with `role="alert"` and the recovery line "Please try again in a moment."; an honest unavailability (the API said so) uses `role="status"`.

## Shapes

Corner radius is a theme token: `--radius` is 10px for malai, 8px for halcyon, 12px for linden, and the `sm`/`md`/`lg`/`xl` steps derive from it (radius −4, −2, +0, +4). Controls (buttons, inputs, menu items) use `lg` and `md`; cards use `xl`. Two shapes step beyond the control range: the hero corner (radius + 6px, so 16px on malai, 14px on halcyon, 18px on linden) on metric tiles, the card art, the bottom sheet and dashed panels, and the full pill on avatars, icon wells, category chips and the active tab's icon pill.

Borders are one weight and one colour everywhere: 1px Hairline. Status tags on the Ops surface square off to `sm` and use a dashed border to mean "no longer live" (dismissed cases, decisions with no ruling expected). The bank card is a 1.586:1 rectangle with the hero corner and a primary-to-75%-primary diagonal gradient, the only gradient in the app; frozen cards get a 60% primary veil with a 2px blur.

Icons are Lucide at 16px inside controls and 20px in list rows and icon wells, 2px stroke; the active mobile tab fills its icon with the current colour and drops the stroke.

## Components

Precise and unhurried. Controls are compact (32px), bordered at 1px, and answer the pointer with colour alone; press is a 1px translate, focus is a 3px translucent ring in the brand colour. Nothing scales, bounces or glows.

### Buttons

- **Shape:** theme `lg` radius (10px on malai), 32px tall with a pointer and 44px on a coarse (touch) pointer, 10px horizontal padding, 14px/500 text, 6px gap to a 16px icon. Sizes step to 24px (`xs`), 28px (`sm`), 36px (`lg`), and square icon variants, and every one of them, icon sizes included, is at least 44px on a coarse pointer; forms on the auth screens use a 44px full-width button with 16px text.
- **Primary:** Espresso Rust fill, Paper on Ink text. No hover change on a button; a primary link lightens to 80% on hover.
- **Outline:** Cream Paper fill with a Hairline border; hovers to Warm Linen. Also the user-menu trigger. In dark mode the fill is 30% Control Stroke tone; the border stays Hairline.
- **Ghost:** transparent; hovers to Warm Linen. Used for icon buttons (back, dismiss) and the eight-way chip filters.
- **Secondary:** Warm Linen fill, hovers to 80%.
- **Destructive:** a 10% Brick Refusal tint with Brick Refusal text, hovering to 20%; never a solid red fill. Confirms destructive intent inside an alert dialog or via a two-press inline confirm on the Ops surface.
- **Link:** brand-coloured text, underline on hover with a 4px offset.
- **Focus / Press / Disabled:** `focus-visible` sets the border to the ring colour and a 3px ring at 50%; active translates 1px down; disabled is 50% opacity with pointer events off.

**The Two Rings Rule.** A bordered control (button, input, OTP slot) shows focus as a 3px ring at 50% plus its border turning the ring colour. A flat focusable surface (link, row, card, tab, icon button) shows a 2px ring at 50% and nothing else. No other opacity, and no ring on anything that is not focused.

### Chips

- **Filter chips** (Worklist views, Case status, Reporting window): buttons in a `role="group"`, outline when idle and primary when selected, each carrying its count at 12px regular weight in tabular figures (weight, not opacity, carries the de-emphasis so the count keeps 4.5:1 on either fill).
- **Category chip:** Warm Linen pill, 12px/500 Soft Cocoa text, 2px × 8px padding. No dot: the label is the identity.
- **Status tag** (Ops decisions and cases, pending and declined transaction rows): 12px/500 text in a 1px bordered `sm`-radius box, 2px × 6px padding. Tone by border: Hairline for neutral, ring colour for active investigation, 40% Brick Refusal with Brick Refusal text for refusals and reports, dashed Hairline with Soft Cocoa text for the inert.

### Cards / Containers

- **Corner Style:** theme `xl` (14px on malai) for account cards and heroes; the hero step (radius + 6px, 16px on malai) for metric tiles and dashed panels.
- **Background:** Card White on Cream Paper.
- **Shadow Strategy:** Whisper at rest; clickable cards add Acknowledge and a 40% ring-coloured border on hover, and a 2px ring on focus.
- **Border:** 1px Hairline.
- **Internal Padding:** 16px for list cards, 24px for heroes and tiles. A hero stacks title (24px/600), subtitle (14px Soft Cocoa), then the Display figure with 24px above it and its hide-balance toggle beside it.

### Inputs / Fields

- **Style:** transparent fill, 1px Control Stroke, theme `lg` radius, 32px tall (44px on a coarse pointer), 10px horizontal padding, 16px text that drops to 14px at 768px. Labels are 14px/500 with 6px below. Auth forms use 44px inputs.
- **Focus:** border to ring colour plus a 3px ring at 50%.
- **Error / Disabled:** `aria-invalid` sets a Brick Refusal border and a 3px ring at 20%; the message sits below at 14px in Brick Refusal and is linked by `aria-describedby`. Disabled is 50% opacity on a 50% input-tone fill.
- **Confirmation code:** six OTP slots (eight for a login code) via `input-otp`, with a blinking caret. Slots are 40px tall (44px on a coarse pointer) and shrink in width so eight of them still fit a 320px viewport.

### Navigation

- **Desktop nav (customer and Ops):** 14px/500 Soft Cocoa links, 40px tall (44px on a coarse pointer), 12px padding, a 2px transparent bottom border that turns ring-coloured with Espresso Ink text when active. Sits under the header on a Cream Paper bar with a Hairline below.
- **Mobile bottom tabs:** four equal columns, 12px/500 captions in Soft Cocoa. The active tab goes bold in Espresso Rust with its icon filled and set in an Apricot Wash pill (28px × 48px, full radius). Fixed to the bottom with a Hairline above and safe-area padding.
- **Header:** 48px, wordmark at 24px tall in the brand colour on the left; theme switcher and the outline user-menu trigger on the right. Staff headers replace the switcher with an uppercase eyebrow badge ("OPS", "EXEC") in a 1px `sm`-radius box beside the wordmark.
- **Dropdown menu:** Card White popover, theme `lg` radius, 4px padding, Held shadow, 36px items at 14px (44px on a coarse pointer) that highlight to Warm Linen; fades in and out on the Arrive curve.
- **Skip link:** present on every shell, visible only on focus.

### Bank card

The signature object. A 1.586:1 rectangle, hero corners (16px on malai), primary gradient, Paper on Ink text, Held shadow. Wordmark at 14px/600 top-left, a 15%-white variant pill top-right, the PAN in 18px/500 at 0.1em tracking in four tabular groups, then the cardholder name uppercase at 12px, expiry and CVV with 10px uppercase captions at 70%, and the VELA scheme mark in bold italic. Frozen: a centred snowflake and "Frozen" over a 60% primary veil. Cancelled: 60% opacity, half saturation.

### Metric tile

Card White, hero corners (16px on malai), 24px padding, Whisper shadow. A 14px/500 Soft Cocoa label, then the Display figure in tabular numerals with 16px above (30px below 640px, so a seven-figure sum still fits a one-column tile). A pending figure is a 36px × 96px pulsing Warm Linen block with a screen-reader "Loading"; an unavailable figure is an em-dash with a screen-reader "Unavailable". Never a zero.

### Data table

The Ops Worklist, Case list, Case evidence and the Sandbox counterparty directory share one table: 14px cells, 12px vertical padding, a Hairline between rows, Soft Cocoa 500 column headers, figures right-aligned in tabular numerals, identifiers in mono. Wide tables sit in a `ScrollRegion`. Below 768px the table stops being a table: each row becomes a Card White card with the hero-radius step down (`xl`), 16px padding and a Whisper shadow, and every cell becomes a label/value line with the column header repeated at 12px Soft Cocoa on the left and the value on the right. Row clicks and links behave the same in both forms, and the header row stays in the accessibility tree for screen readers.

### Spending breakdown

The month's spending as a ranked list, not a ring. A Soft Cocoa "Spent" caption over the Card figure for the total, then one row per category, largest first: a 40px icon well (Warm Linen, Soft Cocoa icon), the category name at 16px/500 with the amount right-aligned in tabular figures, a 6px Warm Linen track beneath with an Espresso Rust fill sized to that category's share of the total, and a 12px Soft Cocoa line giving the share and the transaction count. One hue, thin marks, the numbers in text: the bar is `aria-hidden` because every value it draws is already printed beside it. Rows are 20px apart. The pending state is a figure-sized block and three row-height blocks pulsing in Warm Linen.

### Transaction row

A full-width button, 12px vertical padding, `md` radius, hovering to 40% Warm Linen. A 40px icon well on the left, the primary line at 14px/500 with an inline status tag, a category chip and 12px secondary line beneath, and the amount right-aligned in 500 with a `+` in Credit Green for credits. Pending amounts go Soft Cocoa; declined amounts go Soft Cocoa with a strikethrough.

### Outcome screen

Centred column, 40px vertical padding, 24px gaps. A 48px indicator (spinning loader in the brand colour, or the two-stroke success tick drawing itself in Credit Green over 400ms and 320ms with a 320ms delay, both `cubic-bezier(0.4, 0, 0.2, 1)` and skipped under reduced motion), a 24px title, 14px Soft Cocoa lines, then a single outline button back to the source account.

### Sheet and dialog

The transaction sheet rises from the bottom on mobile (hero top corners, 90dvh cap) and docks to the right at 420px on desktop (16px left corners, full height), arriving over 300ms on the Arrive curve and leaving in 200ms. Alert dialogs are 28rem, centred, `lg` radius, 24px padding, Floating shadow, fading in over 200ms and out in 150ms over a black scrim at 50% (70% in dark mode) that fades with them. Titles at 18px/600, body at 14px Soft Cocoa, actions right-aligned with an 8px gap.

### Toast

The only shell-level live region. Card White on a Hairline border, theme `lg` radius, Floating shadow, top-right on desktop and above the bottom tabs on mobile. Set in Inter at 14px like everything else: the title at 500, the description in Soft Cocoa. Colour never floods the toast; only the 16px icon takes Credit Green for a success or Brick Refusal for a failure, so the toast follows the active brand and the One Ink Rule holds.

## Motion

Nothing bounces, nothing scales, nothing waits for a page to choreograph itself. Motion here does three jobs and no more.

- **One authored moment.** The success tick on the outcome screen draws itself in Credit Green (400ms circle, 320ms check after a 320ms delay). Nothing else on the customer surface is allowed to compete with it; a settled payment does not also fade its container in.
- **Continuity for anything that leaves the page.** The transaction sheet slides in from the bottom on a phone and from the right on desktop; dropdown menus and alert dialogs fade; the scrim fades with whatever it sits under. Arrivals use the **Arrive** curve, `cubic-bezier(0.16, 1, 0.3, 1)` (`ease-arrive`), and exits use `ease-in` at two-thirds of the arrival time: 300/200ms for the sheet, 200/150ms for dialogs and the scrim, 150ms for menus.
- **Feedback where a demo beat changes state.** The Frozen veil settles onto a card over 300ms rather than snapping on; a payment verdict (returned or declined) arrives with a 300ms fade so the presenter can see the screen turn; the confirmation-code caret blinks; a pending figure pulses; a press translates 1px.

The vocabulary is `tw-animate-css` on top of Tailwind, so every entrance is opacity and transform only, and the one blur in the app (the 2px veil on a frozen card) is bounded to that card. Every animation is wrapped in `motion-safe:`; under `prefers-reduced-motion` the sheet, dialogs, veil and verdict appear in their final state, the caret holds steady, the tick lands drawn, and the pulse and spinner stop. Feedback stays legible without movement.

## Do's and Don'ts

### Do:

- **Do** set every money figure, count, identifier and timestamp in `tabular-nums`, and give credits a leading `+` in Credit Green.
- **Do** render an absent figure as an em-dash with a screen-reader "Unavailable", and a pending one as a pulsing Warm Linen block (`bg-muted/40`).
- **Do** use the 1px Hairline border and a tonal step for structure; reserve Held and Floating shadows for menus, dialogs, sheets and the bank card.
- **Do** keep controls at 32px with a pointer and 44px on a coarse pointer (`pointer-coarse:`), with the theme `lg` radius, a 3px 50% ring on focus and a 1px press. That floor covers inline text buttons and links too: pad them vertically on a coarse pointer rather than leaving a 20px line as the whole target.
- **Do** reference colour through the semantic tokens (`bg-primary`, `text-muted-foreground`), never a literal Tailwind hue; the ESLint rule enforces it and the theme switch depends on it.
- **Do** wrap every animation in `motion-safe:` and give it a reduced-motion fallback that lands on the final state.
- **Do** give every page one focusable h1 and, on the Ops surface, show an identifier shortened in mono with the full id a copy away.
- **Do** wrap anything that can outgrow its box (tables, raw model output, diffs) in `ScrollRegion`: it stays a plain container while the content fits and becomes a labelled, keyboard-focusable region the moment it scrolls.
- **Do** render empty and failed states as a dashed Hairline panel with centred Soft Cocoa text, and a refusal as a designed inline fact rather than a toast.

### Don't:

- **Don't** introduce a second hue per theme. Accent, secondary and muted are tints of the paper; the brand colour is the only ink.
- **Don't** colour a debit red or use Brick Refusal for anything that is not a refusal, an error or a destructive action.
- **Don't** put a solid red fill on a destructive button; it is a 10% tint with coloured text.
- **Don't** add a theme switcher, a customer skin or a bottom tab bar to the Ops or Exec surfaces; they pin the malai tokens and use the desktop text nav.
- **Don't** vary typography per theme, and don't load a second family; Inter Variable is app-wide and the only monospace is for Ops identifiers.
- **Don't** drift body copy up from 14px, and don't use Display sizes on a heading; the largest type is always a figure.
- **Don't** use a skeleton shimmer, a zero or a hardcoded value where a real number is missing.
- **Don't** add hover lift, scale, glow, glassmorphism, decorative gradients, mascots or confetti; the card art's diagonal gradient is the one exception and stays there.
- **Don't** use a real card-scheme mark or name; the scheme is VELA.
