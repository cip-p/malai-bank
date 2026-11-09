# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT-MAP.md`** at the repo root — it points at one `CONTEXT.md` per bounded context (one per service). Read each one relevant to the topic.
- **`docs/adr/`** at the repo root — system-wide / cross-service decisions.
- **`malai-services/<domain>-service/docs/adr/`** — decisions scoped to a single service.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront. The producer skill (`/grill-with-docs`) creates them lazily when terms or decisions actually get resolved.

## File structure

Multi-context: `CONTEXT-MAP.md` at the root pointing to per-service `CONTEXT.md` files, with ADRs at both the root and per-service levels.

```
/
├── CONTEXT-MAP.md
├── docs/adr/                                  ← system-wide / cross-service decisions
└── malai-services/
    ├── auth-service/
    │   ├── CONTEXT.md
    │   └── docs/adr/                          ← service-specific decisions
    ├── account-service/
    │   ├── CONTEXT.md
    │   └── docs/adr/
    └── payee-service/
        ├── CONTEXT.md
        └── docs/adr/
```

The bounded contexts in this repo correspond to the service modules under `malai-services/` — currently `auth`, `account`, `account-holder`, `payee`, `direct-debit`, `standing-order`, `profile`. Each gets its own `CONTEXT.md` when terms there start to crystallise.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in the relevant `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal — either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/grill-with-docs`).

## Flag ADR conflicts

If your output contradicts an existing ADR, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0007 (event-sourced orders) — but worth reopening because…_
