# Generated Zod schemas are the source for form validation

`@hey-api/openapi-ts` is configured (`zod` plugin, `requests: true, responses: true`) to emit a Zod schema per endpoint into `src/api/generated/`. Forms use those schemas directly: `useForm({ resolver: zodResolver(zLoginRequest) })`. When a form needs client-only fields or cross-field rules, it `.extend()` / `.refine()`s the generated schema rather than authoring a parallel one. Hand-authored form schemas are not allowed alongside the generated ones — either the contract is the source, or we drop the Zod plugin entirely. We chose the former.

The OpenAPI at `malai-backend/malai-api/src/main/resources/static/openapi.yaml` is hand-authored, so every structural rule a form needs — `minLength`, `pattern`, `format: email` — has a home in the contract. A contract change becomes a TypeScript error at every form that touches the affected fields, which is the failure mode we want. Drift between client validation and server validation becomes impossible at compile time.

## Caveat: Cognito password policy ↔ OpenAPI pattern

Cognito's password policy is configured in `malai-infra` IaC. If the policy changes there, the OpenAPI's `RegisterRequest.password` / `ChangePasswordRequest.newPassword` `pattern` and `minLength` must be updated in lock-step at malai-backend, or the client will pass validation locally and the server (Cognito) will reject. This sync is currently manual — flag for workspace-level discussion if it becomes a recurring papercut.

## Considered options

- **Generated as source, extend on demand (accepted).** Single source of truth; drift impossible at compile time.
- **Always wrap the generated schema in a feature-local schema, even when no extension is needed.** Predictable shape but produces passthrough schemas. Rejected per the webapp CLAUDE.md "don't generalise from one" rule.
- **Hand-author all schemas; drop the Zod plugin.** Independence from the contract at the cost of silent drift between client- and server-side validation. Rejected — the contract is the right source of truth.
