# CLAUDE.md (malai-website)

Repo-local guidance for the Malai marketing site. Cross-module concerns live in the root `CLAUDE.md`, one level up.

## Formatting

Before reporting any change as done, run `npm run prettier:fix` (and verify with `npm run prettier`). The GitHub workflow (`.github/workflows/malai-website.yml`) runs `prettier --check` and fails on any unformatted file — fix it locally rather than letting CI catch it.

## Agent skills

### Issue tracker

Local markdown under `.scratch/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context — `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
