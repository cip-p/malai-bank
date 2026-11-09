# malai-webapp

The Malai web app: a React 19 single-page app behind one passwordless login. It has three
sibling surfaces:

- **Banking**: the customer's accounts, cards, payments, payees, standing orders and direct
  debits.
- **Ops**: the back office. A review worklist for flagged payments and AML cases, decision
  records, and rulings.
- **Exec**: the executive dashboard.

A **Sandbox** simulates inbound payments and card activity during a demo.

It is built with Vite, TanStack Query, React Router, React Hook Form + Zod, shadcn/ui and
Tailwind v4, and tested with Vitest and Testing Library.

## Prerequisites

- Node 24 and npm
- For running against real data: [malai-backend](../malai-backend) running locally, which needs
  JDK 25, Maven and Docker

## Run locally

Start the backend first, from `../malai-backend`:

```bash
./start-docker-containers-local.sh   # Postgres + Localstack
./start-malai-api-local.sh           # malai-api on :8080, with the payment-scheme and Cognito stubs
./demo-reset-local.sh                # in a second terminal: seed the demo users and their data
```

Then start the webapp:

```bash
npm ci
npm run dev                          # http://localhost:5173
```

The app calls the API at `http://localhost:8080` by default. To point it elsewhere, copy
`.env.example` to `.env` and set `VITE_API_BASE_URL`.

To sign in, use a seeded user's email. Sarah Mitchell (`sarah.mitchell@example.com`) is the
main demo customer, and `curl localhost:9095/stub/users` lists every user with their roles. The local Cognito stub
accepts any confirmation code, for example `12345678`.

If the backend isn't running, the app shows a "service paused" page. It recovers on its own once
the API is up.

## Checks

These are the same checks CI runs:

```bash
npm run prettier          # formatting
npm run lint              # ESLint
npm run openapi:verify    # the committed API client matches ./openapi.yaml
npm run build             # type-check and production build into out/
npm test                  # unit and integration tests (Vitest, jsdom)
```

## The API client

`./openapi.yaml` is a snapshot of the backend's hand-authored contract
(`../malai-backend/malai-api/src/main/resources/static/openapi.yaml`). The typed client, the
TanStack Query hooks and the Zod schemas under `src/api/generated/` are generated from it.

When the backend contract changes:

1. Copy the backend's `openapi.yaml` over `./openapi.yaml`.
2. Run `npm run openapi:gen`. It also runs automatically before every build.
3. Fix whatever TypeScript flags.
4. Commit the regenerated client.

## CI

`.github/workflows/malai-webapp.yml` has two jobs:

- **build** runs the formatting, lint, API-client and build checks.
- **test** runs the unit tests.

## Layout

| Path                                                                    | What it is                                                                                                        |
| ----------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `src/surfaces/`, `src/pages/`                                           | The three surfaces and their routes.                                                                              |
| `src/accounts/`, `src/cards/`, `src/move-money/`, `src/account-holder/` | Banking features.                                                                                                 |
| `src/ops/`, `src/exec/`, `src/sandbox/`                                 | The back office, the executive dashboard, and the demo sandbox.                                                   |
| `src/auth/`, `src/availability/`                                        | Passwordless sign-in, and the "service paused" gate.                                                              |
| `src/components/`, `src/theming/`, `src/styles/`                        | Shared UI (shadcn/ui), themes and design tokens.                                                                  |
| `src/api/`                                                              | The HTTP client setup and the generated API client.                                                               |
| `docs/adr/`                                                             | Architecture decision records.                                                                                    |
| `CONTEXT.md`, `PRODUCT.md`, `DESIGN.md`                                 | The domain glossary, the product brief and the design system.                                                     |
| `scripts/generate-pwa-icons.mjs`                                        | Regenerates the PWA icons in `public/`. Needs Playwright's Chromium: run `npx playwright install chromium` first. |
