---
name: Malai
description: A due-diligence room on warm paper — bank ink, one verified green, hairlines, and evidence beside every claim.
colors:
  bank-ink: "#0b1e3a"
  bank-ink-hover: "#1c3a6a"
  bank-ink-outline: "#2a4d85"
  bank-ink-wash: "#f3f6fb"
  bank-ink-selection: "#e6ecf5"
  verified-green: "#0f6b54"
  verified-green-deep: "#0a5343"
  verified-green-focus: "#16855c"
  verified-green-wash: "#e6f4ee"
  warm-white: "#fafaf7"
  warm-paper: "#f3f1ea"
  ink: "#14171f"
  ink-muted: "#5b6478"
  ink-faint: "#636c7f"
  rule: "#e4e6eb"
typography:
  display:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "2.25rem"
    fontWeight: 600
    lineHeight: 1.05
    letterSpacing: "-0.025em"
    fontFeature: "'ss01' on, 'cv11' on"
  headline:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "1.875rem"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.025em"
  title:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.5
    letterSpacing: "-0.025em"
  lede:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "1.125rem"
    fontWeight: 400
    lineHeight: 1.625
    letterSpacing: "normal"
  body:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.625
    letterSpacing: "normal"
  action:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "1rem"
    fontWeight: 500
    lineHeight: 1.5
    letterSpacing: "normal"
  label:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    lineHeight: 1.5
    letterSpacing: "0.16em"
  mono:
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace"
    fontSize: "1.125rem"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "normal"
rounded:
  sm: "4px"
  md: "6px"
  lg: "8px"
  xl: "12px"
  full: "9999px"
spacing:
  "1": "4px"
  "2": "8px"
  "3": "12px"
  "4": "16px"
  "5": "20px"
  "6": "24px"
  "8": "32px"
  "10": "40px"
  "12": "48px"
  "16": "64px"
  "20": "80px"
  "24": "96px"
  "32": "128px"
components:
  button-primary:
    backgroundColor: "{colors.bank-ink}"
    textColor: "{colors.warm-white}"
    typography: "{typography.action}"
    rounded: "{rounded.md}"
    padding: "12px 20px"
  button-primary-hover:
    backgroundColor: "{colors.bank-ink-hover}"
  button-primary-compact:
    backgroundColor: "{colors.bank-ink}"
    textColor: "{colors.warm-white}"
    rounded: "{rounded.md}"
    padding: "8px 14px"
  button-secondary:
    backgroundColor: "{colors.warm-white}"
    textColor: "{colors.bank-ink}"
    typography: "{typography.action}"
    rounded: "{rounded.md}"
    padding: "12px 20px"
  link-inline:
    textColor: "{colors.verified-green}"
  link-inline-hover:
    textColor: "{colors.verified-green-deep}"
  eyebrow:
    textColor: "{colors.verified-green}"
    typography: "{typography.label}"
  card:
    backgroundColor: "{colors.warm-white}"
    rounded: "{rounded.lg}"
    padding: "{spacing.6}"
  card-paper:
    backgroundColor: "{colors.warm-paper}"
    rounded: "{rounded.lg}"
    padding: "{spacing.6}"
  panel:
    backgroundColor: "{colors.warm-paper}"
    rounded: "{rounded.lg}"
    padding: "{spacing.8}"
  stat-cell:
    backgroundColor: "{colors.warm-white}"
    textColor: "{colors.bank-ink}"
    typography: "{typography.mono}"
    padding: "24px 20px"
  icon-tile:
    backgroundColor: "{colors.verified-green-wash}"
    textColor: "{colors.verified-green-deep}"
    rounded: "{rounded.md}"
    size: "36px"
  nav-link:
    textColor: "{colors.ink-muted}"
  nav-link-active:
    textColor: "{colors.bank-ink}"
  mobile-nav-item:
    textColor: "{colors.bank-ink}"
    padding: "16px 24px"
  mobile-nav-item-active:
    backgroundColor: "{colors.bank-ink-wash}"
    textColor: "{colors.verified-green}"
  screenshot-frame:
    backgroundColor: "{colors.warm-white}"
    rounded: "{rounded.lg}"
---

# Design System: Malai

## Overview

**Creative North Star: "The Due-Diligence Room"**

A quiet, well-lit room where a buyer reads the file. The page is warm paper, the words are bank ink, and one green stamp marks the places where a claim is backed by evidence or a link leads to proof. Nothing decorates. The visual system exists so that a bank executive or platform lead can read, check, and decide without being sold to, and so that code, diagrams and product screenshots feel native rather than pasted in. This is the builder-confident neutral register recorded in ADR-0002: it is deliberately not an institutional serif-and-navy bank site and not a consumer-fintech gradient site.

The mood is calm, exact, evidenced, unhurried. Density is moderate: generous section spacing, a single content column that never exceeds 1152px, prose capped at roughly 48rem, and cards that hold one claim each. Depth is tonal and hairline, not shadowed. Motion is limited to colour transitions; nothing lifts, glows or bounces. Real screenshots of the demo carry the visual load where a marketing site would normally reach for illustration or stock photography.

**Key Characteristics:**

- Warm-white ground with warm-paper panels; navy ink, never pure black, for every heading
- One accent, Verified Green, reserved for eyebrows, links, the mark and evidence references
- Hairline rules do the structural work: borders, dividers, and the 1px gutters of the stat strip
- System sans throughout, with a monospace voice for figures and technical values
- Screenshot frames drawn as small browser windows, the site's only shadowed element
- Flat cards that respond to hover by darkening their border, not by rising

## Colors

A monochrome-plus-one palette: warm neutrals for surfaces, navy and slate for ink, and a single muted green that appears only where something is verifiable.

### Primary

- **Bank Ink** (`bank-ink`): The ink a bank writes in. Every heading, the wordmark, the primary button, the mobile-menu label text, and the diagram's box strokes. It is the colour of authority on the page, and it never appears as a background wash except on the primary button.
- **Bank Ink Hover** (`bank-ink-hover`): The primary button's hover fill. The only lighter navy that appears as a fill.
- **Bank Ink Outline** (`bank-ink-outline`): The hover border for cards, the secondary button and the mobile toggle. A card that darkens its hairline to this navy is the system's whole hover vocabulary.
- **Bank Ink Wash** (`bank-ink-wash`): A barely-there navy tint used for the active and hovered mobile-nav row.
- **Bank Ink Selection** (`bank-ink-selection`): Text selection highlight, with Bank Ink as the selected text colour.

### Secondary

- **Verified Green** (`verified-green`): The stamp. Section eyebrows, inline text links and their arrows, the shield-and-keyhole mark in header and footer, the bullet dots in the Copilot list, and the left rule of pull quotes and the FSMA disclaimer. It appears where a claim meets its evidence or a reader is invited to go and check.
- **Verified Green Deep** (`verified-green-deep`): Link hover and the icon inside category tiles.
- **Verified Green Focus** (`verified-green-focus`): The 2px focus-visible outline on every interactive element, offset by 2px. It is the one place a slightly brighter green is allowed.
- **Verified Green Wash** (`verified-green-wash`): The fill behind category icons, a 36px tile that introduces each trust-ledger and architecture group.

### Neutral

- **Warm White** (`warm-white`): The page ground and the default card face. Faintly warm so the navy reads as ink on paper rather than text on a screen.
- **Warm Paper** (`warm-paper`): A second, slightly deeper paper for the footer, the mobile menu panel, evidence cards, feature panels, the walkthrough box and the diagram figure. It is the only way a region is set apart from the page.
- **Ink** (`ink`): Long-form body text and evidence detail copy.
- **Ink Muted** (`ink-muted`): Ledes, card body copy, inactive nav links, the footer description and the disclosure line.
- **Ink Faint** (`ink-faint`): Captions, step and audience labels, footer column headings, pending-evidence text and the screenshot chrome URL.
- **Rule** (`rule`): Every hairline: card borders, the sticky header's bottom edge, section dividers, stat-strip gutters, mobile-menu row dividers and the 2px walkthrough step rules.

### Named Rules

**The One Stamp Rule.** Verified Green is used only on eyebrows, links, the mark, focus rings, icon tiles and evidence rules. It never fills a button, a card or a section. Its rarity is what makes an evidence link read as a stamp.

**The Ink-Not-Black Rule.** Headings are Bank Ink and body text is Ink; pure black appears nowhere on the site. The favicon and Open Graph card use a near-black tile because they live outside the page.

**The Paper Layer Rule.** Regions are set apart with Warm Paper on Warm White, never with a tinted colour or a gradient. If a third surface colour seems necessary, the layout needs a hairline instead.

## Typography

**Display Font:** System sans (`ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans"`), with stylistic set 1 and the single-storey `a` alternate enabled so Inter-class system fonts render with a cleaner geometric character.
**Body Font:** The same system sans.
**Label/Mono Font:** System monospace (`ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New"`), used for figures, versions, standards names and the 404 code.

**Character:** A single geometric sans doing all the talking, tightened at display size and opened up with wide uppercase tracking at label size. The monospace voice is reserved for values a technologist would want to copy: `TLS 1.2+`, `HSTS preload`, `OWASP CRS`.

### Hierarchy

- **Display** (600, 2.25rem → 3rem at `sm` → 3.75rem at `md`, line-height 1.05 → 1.1, tracking -0.025em): The page `h1`. Capped at 48rem wide. Bank Ink.
- **Headline** (600, 1.875rem → 2.25rem at `sm`, line-height 1.25, tracking -0.025em): Section `h2`. Preceded by an eyebrow on deep pages; the home page sections omit the eyebrow except Security and Architecture.
- **Panel title** (600, 1.25rem, tracking -0.025em): The `h3` inside a panel such as the walkthrough, the Copilot feature or the seam explainer.
- **Title** (600, 1rem, tracking -0.025em): Card `h3`/`h4` and the uppercase category headings. Bank Ink.
- **Lede** (400, 1.125rem → 1.25rem at `sm` in heroes, line-height 1.625): The paragraph under every headline. Ink Muted, capped at 42rem in heroes and 48rem in sections.
- **Body** (400, 0.875rem, line-height 1.625): Card copy and evidence detail. Long-form prose on About, Privacy and Terms steps up to 1rem.
- **Action** (500, 1rem, or 0.875rem in the header and About CTA): Button labels and standalone links.
- **Label** (600, 0.75rem, tracking 0.16em, uppercase): The eyebrow above a headline, always Verified Green. Sub-labels such as `Step 1` or `For the reviewer` drop to 500 weight, 0.025em tracking and Ink Faint. Evidence kickers (`Note`, `Reference`, `Pending evidence`) are 10px at 0.14em.
- **Mono** (600, 1.125rem → 1.25rem at `sm`, line-height 1.25): Stat values in the four-cell strips. Bank Ink.

### Named Rules

**The Two Weights Rule.** Text is either 600 (anything that names something) or 400 (anything that explains it), with 500 only for actions and sub-labels. There is no bold, no light, and no italic beyond the pull quote and pending-evidence note.

**The Tight-Up, Wide-Down Rule.** Anything at or above 1rem that acts as a heading is tracked tight; anything at or below 0.75rem that acts as a label is uppercase and tracked wide. Nothing in between is letter-spaced.

## Layout

One centred column, 72rem (1152px) wide, with 1.5rem (24px) side padding at every breakpoint. Reading measure is enforced inside it: headlines and ledes sit in a 48rem block, hero copy in 42rem, so the column's right half is often empty on desktop by design. The only full-width elements are the sticky header bar and the footer band.

Vertical rhythm is section-scale. Home-page sections use 5rem top and bottom (80px), growing to 6rem at `sm`; the hero and the deep pages use 4rem → 6rem top and 5rem → 8rem bottom. The footer sits after a 6rem margin. Inside a section, the headline-to-lede gap is 1.5rem, the lede-to-grid gap is 3rem, and card-to-card gaps are 1.5rem (surface, capability and executive grids) or 1rem → 1.25rem at `md` (trust-ledger and annotation grids).

Grids are simple: three columns at `lg` for surfaces and capabilities (two at `sm` for capabilities), two at `md` for trust-ledger entries, four at `sm` for stat strips (two below), and a six-step walkthrough that runs one column, then three at `md`, then six at `lg`. Deep-page walkthroughs alternate text and screenshot in a two-column grid at `lg`, swapping order on every second step. Category headers on Security and Architecture use a 12-column grid: label and blurb in the left four, a hairline across the right eight.

Breakpoints follow Tailwind defaults: `sm` 640px, `md` 768px, `lg` 1024px. The desktop nav list appears at `lg`; below that a hamburger opens a full-width paper panel of stacked rows. The architecture diagram ships as two SVGs, a desktop layout from `sm` and a stacked mobile layout below it.

## Elevation & Depth

Flat by rule, frames excepted. Surfaces are flat at rest and stay flat under interaction; depth is conveyed by the tonal step from Warm White to Warm Paper and by 1px Rule hairlines. The sticky header is the same paper as the page at 85% opacity with a backdrop blur, separated by a hairline rather than a shadow.

The single exception is the screenshot frame. Each product screenshot is drawn as a small browser window with a paper title bar and three traffic-light dots, and it carries a faint shadow because it is a window into another product, not a surface of this one. The mobile menu panel borrows the same faint shadow while open.

### Shadow Vocabulary

- **Frame** (`box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.05)`): Screenshot frames and the open mobile menu panel only.

### Named Rules

**The Window Rule.** Only a screenshot frame may cast a shadow, because it shows something that lives outside this page. Cards, panels, buttons and stat strips never do.

**The Border-Darkens Rule.** Hover on a card or secondary button is expressed by its hairline changing from Rule to Bank Ink Outline over a colour transition. Nothing translates, scales or gains a shadow on hover.

## Shapes

Softly squared. Cards, panels, stat strips, the diagram figure and screenshot frames use an 8px radius; buttons, icon tiles and the mobile toggle use 6px; the next-steps panel that closes the About and What this isn't pages is the one 12px exception; the skip link uses 4px. Full-round appears only on the 6px traffic-light dots and Copilot bullets.

Every container has a 1px Rule border. There are no borderless cards, no filled cards on a filled background, and no rounded pill buttons. Stat strips are built as a bordered, radiused container with a Rule background and 1px gaps, so the cells read as one ruled table rather than four cards.

Inside the architecture diagram, the same language holds in SVG: 10px radius on the outer groups, 8px on the gateway rows, 6px on the small service boxes, all with 1px to 1.2px strokes in the current text colour. Synchronous edges are solid with an ink arrowhead; asynchronous edges are dashed (5 on, 4 off) with a Verified Green arrowhead.

## Components

Precise and unhurried. Nothing lifts, glows or bounces; a state change is a border darkening or a colour shifting over a short transition.

### Buttons

- **Shape:** Softly squared (6px radius), inline-flex, no border on the primary.
- **Primary:** Bank Ink fill with Warm White text, 500 weight, 1rem, padded 12px vertical and 20px horizontal. Used for `Try the demo`, page CTAs and the 404 return link. The header carries a compact variant at 0.875rem with 8px by 14px padding.
- **Hover / Focus:** Fill moves to Bank Ink Hover over a colour transition; the underline that links inherit is suppressed. Focus-visible draws a 2px Verified Green Focus outline offset 2px.
- **Secondary:** Warm White fill, 1px Rule border, Bank Ink text at the same size and padding. Hover darkens the border to Bank Ink Outline. Used for `Talk to us` beside every primary.
- **Icon toggle:** A 36px square with the secondary button's border and hover, holding a 20px stroked icon, centred inside a 44px hit area so the visible control keeps the header's proportions while the touch target meets the 44px guidance. The focus ring draws around the 36px square. Only the mobile menu toggle uses it.

### Links

- **Inline and standalone:** Verified Green, 500 weight, hover to Verified Green Deep. Standalone links carry a trailing arrow character in the copy (`→` outward or forward, `↓` for an in-page anchor). Body links underline on hover with a 3px offset; link-styled CTAs inside cards keep the same colour pair.
- **Navigation:** Ink Muted at 0.875rem, hover and active to Bank Ink; the active link steps up to 500 weight and carries `aria-current`.

### Cards / Containers

- **Corner Style:** 8px.
- **Background:** Warm White for surface, capability and stat cells; Warm Paper for trust-ledger entries, architecture annotations, executive points, principles, anti-claims and the AML and seam point lists.
- **Shadow Strategy:** None. See The Window Rule.
- **Border:** 1px Rule, darkening to Bank Ink Outline on hover for any card that is a reading unit.
- **Internal Padding:** 24px. Small point cards use 20px. Panels (walkthrough, Copilot feature, seam explainer, evidence panel, next steps) use 32px, growing to 40px at `sm`.
- **Anatomy:** Title (1rem, 600, Bank Ink), 12px gap, body (0.875rem, Ink Muted), then an optional proof link or screenshot after 16px. Cards in a grid stretch to equal height with the body growing to push the link to the bottom.

### Evidence Card (signature)

The trust-ledger entry is the site's signature unit. A Warm Paper card holds the claim as its title, the detail in Ink at 0.875rem, then a hairline after 20px and an evidence footer: a 10px uppercase kicker in Ink Muted (`Note`, `Reference` or `Pending evidence`) over 0.75rem evidence text. A reference is a Verified Green link; a note is Ink Faint; pending evidence is Ink Faint italic. The kicker tells the reader what kind of proof they are looking at before they read it.

### Stat Strip

Four cells (two per row below `sm`) inside one bordered 8px container whose Rule background shows through 1px gaps. Each cell is Warm White, padded 24px by 20px, with an uppercase 0.75rem Ink Muted label over a monospace 600 value in Bank Ink. It opens every deep page and is the first place the technologist finds a number.

### Category Header

A 36px Verified Green Wash tile with a 20px stroked icon in Verified Green Deep, beside a 1rem uppercase 600 title in Bank Ink; a blurb in Ink Muted below; and, from `md`, a hairline that runs across the remaining eight columns level with the title. Used to introduce each group of the trust ledger and the architecture annotations, and the diagram itself.

### Screenshot Frame

A bordered 8px container with a Warm Paper title bar (padded 6px by 12px) holding three 6px dots in translucent red, amber and emerald, an optional monospace 9px URL, and the image below at full width. Responsive `srcset` widths are 640, 1280 and 2560 by default, with 320, 640 and 1280 in grids. The frame is the only place off-token colour appears, and only in the dots.

### Navigation

- **Header:** Sticky, 56px tall, Warm White at 85% with backdrop blur and a Rule hairline below. Wordmark on the left is the 16px shield-and-keyhole mark in Verified Green beside `Malai` at 1.125rem 600 tight. Links sit in the centre from `lg`; a compact primary button and, below `lg`, the icon toggle sit on the right.
- **Mobile panel:** A Warm Paper panel under the header with rows divided by Rule hairlines. Each row is 1rem 500 Bank Ink, padded 16px by 24px, with an Ink Faint chevron on the right. Hover and active rows take the Bank Ink Wash background and Verified Green text. Escape and any link tap close it.
- **Footer:** Warm Paper band with a Rule top edge, padded 56px vertical. A wide first column carries the mark, wordmark and description; three link columns follow with 0.75rem uppercase 0.12em Ink Faint headings and 0.875rem Ink links. The disclosure sits under a hairline in 0.75rem Ink Muted, the copyright below in Ink Faint.

### Pull Quote and Disclaimer

A 2px Verified Green left rule with 16px inset, 0.875rem text in Ink. The About pull quote is italic; the FSMA disclaimer on What this isn't is upright.

### Architecture Diagram

An inline SVG in a Warm Paper figure, drawn entirely in the current text colour with Warm White box fills. Font sizes 12 to 16, weights 500 and 600, group labels uppercase at 0.1em tracking. It is the site's one illustration and it follows every rule the HTML does.

## Do's and Don'ts

### Do:

- **Do** set every heading in Bank Ink at 600 with -0.025em tracking, and every body paragraph in Ink or Ink Muted at 400.
- **Do** keep Verified Green for eyebrows, links, the mark, focus rings, icon tiles and evidence rules only, per The One Stamp Rule.
- **Do** give every container a 1px Rule border and an 8px radius, and express hover as the border moving to Bank Ink Outline.
- **Do** set regions apart with Warm Paper on Warm White and hairlines, never with tinted fills or gradients.
- **Do** frame every product screenshot in the browser-window frame with real `alt` text and responsive widths.
- **Do** pair every claim with an evidence footer, and mark the kind of evidence with the 10px kicker before the text.
- **Do** put figures, versions and standards names in the monospace face.
- **Do** keep the reading measure: headlines and ledes in a 48rem block, hero copy in 42rem, inside the 72rem column.
- **Do** draw a 2px Verified Green Focus outline offset 2px on every interactive element.

### Don't:

- **Don't** use pure black, a serif face, or a gradient anywhere on the page.
- **Don't** add shadows to cards, panels, buttons or stat strips; only screenshot frames and the open mobile menu may cast one.
- **Don't** translate, scale or lift anything on hover; the only motion is a colour transition.
- **Don't** fill a button, card or section with Verified Green, or use it for body text.
- **Don't** introduce stock photography or decorative illustration; screenshots and the architecture diagram carry the imagery.
- **Don't** use bold, light or italic weights beyond the italic pull quote and pending-evidence note.
- **Don't** use pill buttons, borderless cards or cards nested on a same-colour surface.
- **Don't** letter-space anything between 0.75rem and 1rem; tight above, wide-uppercase below.
