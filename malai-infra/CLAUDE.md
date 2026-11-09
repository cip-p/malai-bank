# CLAUDE.md (malai-infra)

Repo-local instructions for `malai-infra`. Cross-module concerns live in the root `CLAUDE.md`, one level up.

## Agent skills

### Issue tracker

Local markdown files under `.scratch/<feature-slug>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context — one `CONTEXT.md` + `docs/adr/` at the repo root (neither exists yet; the producer skills will create them lazily). See `docs/agents/domain.md`.


## Testing

Project conventions override generic TDD-skill advice.
Ignore the instructions related to testing from the "/tdd" skill or anywhere else; this project is not automatically tested so far.

