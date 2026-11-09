# Malai modernization plan

Tracking the architectural changes worth doing before we go heavy on prod.
Tiers reflect importance for a banking-grade system. Order within a tier is
suggested execution order; tiers are roughly independent.

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done · `[-]` deferred / not doing

---

## Tier 1 — change while it's still cheap

- [x] **CMKs + Secrets Manager rotation** *(via RDS-managed master password)*
  - [x] New `kms/` module owns both CMKs (independent shared infra unit).
  - [x] CMK `mal-${env}-secrets` for Secrets Manager values (RDS master credentials, cognito client).
  - [x] CMK `mal-${env}-logs` for CloudWatch Log Groups (ECS task, lambda authorizer, rotation-redeploy lambda).
  - [x] Wired CMKs onto resources + IAM grants (ECS execution role + lambda authorizer role get `kms:Decrypt` on secrets CMK; logs CMK key policy grants `logs.${region}.amazonaws.com` directly).
  - [x] Switched RDS to `manage_master_user_password = true` — AWS owns the secret (`rds!db-...`), AWS rotates it. No SAR app, no custom rotation lambda. Avoided `serverlessrepo:*` IAM bootstrap headaches in the process.
  - [x] Force-redeploy hook: EventBridge rule on `RotationSucceeded` → small Python lambda calls `update-service --force-new-deployment` so ECS tasks pick up rotated credentials. Lambda authorizer's pool dies on cold start so it doesn't need a hook.
  - Verified: aliases exist, RDS-managed secret encrypted with secrets CMK, ECS rolled onto task def revision 13, end-to-end auth flow works (`/version` 200, lambda-authorizer denial path returns 401 + 1001).

- [x] **WAFv2 in front of the API**
  - [x] WebACL with managed rules: Common Rule Set (OWASP), Known Bad Inputs (Log4Shell etc.), Amazon IP Reputation List, plus a 2000 req / 5 min / IP rate rule.
  - [x] Associated with the API Gateway stage `v1` (REGIONAL scope).
  - [x] CloudWatch metrics enabled on each rule; full request logging deferred to Tier 3 observability.
  - [x] Added `wafv2:*` to the terraform deploy role policy (was missing — only `waf:*` Classic was present).
  - WAF blocks default to 403 ForbiddenException, which our `DEFAULT_4XX` gateway response maps to 401 + `{"message":"1099"}` for the client. Verified end-to-end on the public URL: legitimate request 200, XSS query / Log4Shell header / path traversal all blocked.
  - Alarm on sustained block rate deferred — needs an SNS topic, picked up with Tier 3 observability.

- [x] **HTTP API v2 + Cognito JWT authorizer (drop lambda authorizer + jwt_tokens table)**
  - [x] **Spring side**: dropped `JwtTokenService`/`JwtTokenRepository`/`JwtTokenEntity`. Login returns Cognito tokens directly (`accessToken`/`refreshToken`/`idToken`). Refresh-token endpoint takes the refresh_token in the request body. `/select-account-holder` removed — clients send `MAL-Account-Holder-Id` per request. `JwtDecoderFilter` reads `Authorization: Bearer <jwt>`; `JwtDecoder` requires `token_use == "access"`. `jwt_tokens` table dropped via Flyway V15.
  - [x] **Infra side**: replaced `aws_api_gateway_rest_api` with `aws_apigatewayv2_api` + native JWT authorizer pointing at Cognito JWKS. NLB → internal ALB. New VPC Link v2 connects HTTP API to ALB (no `enforce_security_group_inbound_rules_on_private_link_traffic` gotcha).
  - [x] Custom domain switched to REGIONAL (HTTP API v2 doesn't support EDGE) — AWS-managed CloudFront in front of the gateway is gone, so the `x-cache: cloudfront` headers / `x-amz-cf-*` set are no longer leaked.
  - [x] Lambda authorizer module destroyed end-to-end.
  - [x] `ci.sh` simplified — `publish_openapi` action removed (HTTP API v2 routes are owned by terraform). `OpenApiConfig` cleared of v1-specific `x-amazon-apigateway-*` extensions (the spec is now purely human-facing for Swagger UI).
  - **Caveat**: WAFv2 doesn't support HTTP API v2 stages, so the WebACL is now associated with the **ALB** instead. OWASP/SQLi/XSS/Log4Shell rules still inspect requests; **per-IP rate limiting and IP reputation list are degraded** — they see the VPC Link ENI's IP, not the real client. Restoring those needs a customer-managed CloudFront in front of the API (added below).
  - Verified: `/version` 200, `/api/v1/account-holder/all` (no auth) → 401 Unauthorized from the JWT authorizer with `WWW-Authenticate: Bearer`.

- [-] **Custom domain → REGIONAL** (done — fell out of HTTP API v2 switch above)

## Tier 1.5 — fallout from HTTP API v2 migration

- [x] **Customer-managed CloudFront in front of HTTP API v2 + WAF on CloudFront**
  - [x] CloudFront distribution (`cloudfront.tf`) with origin = HTTP API v2 regional execute-api endpoint. Stage renamed `v1` → `$default` so URLs come through clean (no `/v1` prefix).
  - [x] WebACL switched from REGIONAL (on ALB) → CLOUDFRONT (on the distribution). Rate limit + IP reputation rules now see real client IPs at the edge. Bad payloads rejected at edge before reaching the API/ALB/ECS. Verified with XSS query → 403 from `server: CloudFront`.
  - [x] Response Headers Policy strips `apigw-requestid` (HTTP API v2's primary fingerprint header). CloudFront's own response headers (`x-amz-cf-id`, `via`, `x-amz-cf-pop`, `x-cache`) remain — they're added after the policy runs and need Lambda@Edge to strip; not worth the complexity for marginal opacity.
  - [x] Route 53 alias `api.app.fcd8ayxqr72.com` → CloudFront. The old `aws_apigatewayv2_domain_name` is gone.
  - [x] Verified end-to-end through CloudFront: `/version` 200, missing-auth 401, login flow, authenticated requests, all 7 smoke tests pass.

- [ ] **Block direct execute-api access (CloudFront origin verification)** — **DO BEFORE GOING LIVE**
  - The regional endpoint `{api-id}.execute-api.us-east-1.amazonaws.com` stays publicly reachable. Anyone who knows the api-id (~52 bits of entropy, but leaks easily through stack traces / terraform state / HAR files / AWS Config; once leaked it's permanent without recreating the API) can hit the API directly, bypassing CloudFront and therefore:
    - WAF rate limit → volumetric attacks against `/api/v1/auth/login` (credential stuffing) and other unauth endpoints (cost amplification + autoscale exhaustion)
    - WAF IP reputation list → known-bad sources can hit the API freely
    - WAF OWASP / Known Bad Inputs / Log4Shell rules → defense-in-depth gone, Spring's own input validation becomes the only line
  - JWT authorizer + Spring's filter still defend against impersonation — an attacker can't reach authenticated endpoints. But the volumetric and credential-stuffing exposure is real.
  - **Implementation**: CloudFront sends a shared-secret header (`X-Origin-Verify: <random-from-secrets-manager>`) to the origin via `origin.custom_header`. API Gateway has a tiny Lambda authorizer (or a Spring-side filter) that rejects requests without the matching value. Secret rotates via the same Lambda+Secrets-Manager pattern we already use for RDS.
  - Sketch:
    1. New Secrets Manager secret `mal-${env}-cloudfront-origin-secret`, KMS-encrypted with the secrets CMK, populated by terraform with `random_password`.
    2. CloudFront origin gains `custom_header { name = "X-Origin-Verify", value = ... }` referencing the secret value (read via `data.aws_secretsmanager_secret_version`).
    3. Either:
        - **Option A (recommended)**: tiny Spring filter checks the header on every request. Simpler, no Lambda, no cold start. The filter is dev-mode-bypassable for the smoke test on `MAL_LOCAL=1`.
        - **Option B**: re-introduce a tiny Lambda authorizer at the API Gateway level. Adds cold-start back to the auth path; not recommended.
  - Acceptance: `curl https://{api-id}.execute-api.us-east-1.amazonaws.com/version` returns 401/403; `curl https://api.app.fcd8ayxqr72.com/version` still 200. Smoke tests pass. Secret rotation cycles the value without breaking the running service.

## Tier 2 — before customer-facing

- [ ] **Multi-AZ RDS (prod only)**
  - [ ] Set `multi_az = true` on prod tfvars when promoting
  - Acceptance: `aws rds describe-db-instances` shows `MultiAZ: true` on prod.

- [ ] **RDS backup strategy**
  - [ ] `backup_retention_period` ≥ 30 (currently default 7)
  - [ ] PITR enabled (default with backups; verify)
  - [ ] `final_snapshot_identifier` set, `skip_final_snapshot = false` for prod
  - [ ] Cross-region snapshot replication for prod (DR)
  - Acceptance: snapshot taken, restore-to-PITR tested in dev once.

- [ ] **CloudTrail + GuardDuty + Security Hub**
  - [ ] CloudTrail multi-region trail with log-file validation, KMS-encrypted, S3 + CloudWatch
  - [ ] GuardDuty enabled (~$30/mo for this footprint)
  - [ ] Security Hub enabled with AWS Foundational Security Best Practices standard
  - Acceptance: a deliberate "bad" event (e.g. console root sign-in attempt) shows up in GuardDuty.

- [ ] **Egress hardening**
  - [ ] VPC endpoints: ECR (api + dkr), Secrets Manager, KMS, CloudWatch Logs, STS
  - [ ] Tighten ECS task SG egress: only RDS port to RDS SG, 443 to VPC endpoints, Cognito IPs (or Cognito VPC endpoint if available)
  - [ ] Same hardening for lambda authorizer SG (until it goes away)
  - Acceptance: tasks still pull images, write logs, call Secrets Manager/Cognito; `0.0.0.0/0` egress rule removed.

- [x] **ECS exec enabled**
  - [x] `enable_execute_command = true` on `aws_ecs_service.this`
  - [x] Task role granted the four `ssmmessages:*` channel actions (no `*` wildcard).
  - [x] Cluster's `execute_command_configuration { logging = "DEFAULT" }` reuses the task's `awslogs` config — exec sessions land in the same `/aws/ecs/${name_prefix}-malai-api` log group, encrypted with the logs CMK.
  - Operator side: install `session-manager-plugin` locally (`brew install --cask session-manager-plugin` on macOS) — separate from the standard AWS CLI.
  - Verified: `aws ecs execute-command --task <id> --container malai-api --interactive --command /bin/sh` opens a shell on the running Fargate task.

- [ ] **Tighten ECS exec for prod (follow-up to ECS exec)**
  - Today both the dev account admin role and the CI deploy role can `ecs:ExecuteCommand` against any task. Inside the container, all task env vars (including `MAL_AWS_COGNITO_CLIENT_SECRET`) are visible via `env` — same risk surface as reading the source secret directly via `secretsmanager:GetSecretValue`.
  - For prod, scope `ecs:ExecuteCommand` to a dedicated incident-response IAM role gated behind SSO group + MFA. Deploy role should NOT be able to exec into prod.
  - Audit channel: exec sessions log to the task's awslogs group; build a CloudWatch alarm on `"ecs-execute-command"` filter pattern so any prod exec triggers a notification.
  - Consider rotating Cognito user pool client secret periodically (Cognito doesn't auto-rotate; small Lambda + Secrets Manager rotation, similar pattern to the RDS rotation already in place).
  - Acceptance: dev admin can no longer exec into prod tasks; an incident-response role assumption + exec session triggers a PagerDuty/Slack alert.

## Tier 3 — observability and supply chain

- [x] **Distributed tracing (OpenTelemetry → X-Ray)** — wired, agent dormant pending image release.
  - [x] AWS Distro for OpenTelemetry (ADOT) collector sidecar in the malai-api ECS task, OTLP/gRPC over localhost (`ecs-malai-api/main.tf` `aws-otel-collector` container).
  - [x] OpenTelemetry Java agent baked into the malai-api image (see `malai-api/Dockerfile`); attached at JVM start via `JAVA_TOOL_OPTIONS=-javaagent:/opt/otel/opentelemetry-javaagent.jar`.
  - [x] Task role granted `xray:Put*` + sampling perms (`task_inline.AdotXrayCloudWatch`).
  - [x] X-Ray + W3C trace propagation; sampler `parentbased_traceidratio` at 0.1.
  - [ ] **Activation** is gated on `var.enable_otel_agent` (default `false`) so we don't attach the agent to images that predate the Dockerfile change (would crash the JVM on boot). To activate after the next release: set `enable_otel_agent = true` in `config/malai/dev/dev.tfvars`, lift the `ignore_changes = [container_definitions]` lifecycle in `ecs-malai-api/main.tf`, terraform apply, restore the lifecycle, force a deployment.
  - Acceptance (deferred until activated): a request through CloudFront → API Gateway → ECS → RDS shows up as a single linked trace in the X-Ray console.
  - Notes: API Gateway stage is HTTP API v2 — X-Ray Active Tracing on the stage itself isn't supported (REST v1 only); we rely on the agent on the backend instead. The W3C `traceparent` propagator is enabled, so a future fronting proxy could inject one and we'd thread it through.

- [x] **Structured logs + alerts**
  - [x] Spring Boot logs as JSON (Logback `LogstashEncoder` — see `malai-api/src/main/resources/logback-spring.xml`, active when `SPRING_PROFILES_ACTIVE` is `dev` or `prod`).
  - [x] CloudWatch metric filter on `{ $.level = "ERROR" }` (`ecs-malai-api/alarms.tf`).
  - [x] SNS topic per env (`<prefix>-malai-api-alerts`), with `var.alert_emails` hook for subscribers (subscriptions need manual confirmation).
  - [x] Alarms: ECS CPU + memory, ALB target 5xx, ALB LB 5xx, RDS CPU, RDS connections, RDS free-storage.
  - Acceptance: a forced 500 in dev triggers the `app-errors` alarm within ~5 minutes.
  - Note: `var.alert_emails` defaults to `[]` for dev — set this in `config/malai/dev/dev.tfvars` to wire a recipient.

- [x] **Container scanning + SBOM**
  - [x] ECR `image_scanning_configuration { scan_on_push = true }` (already in `shared-infra/shared-ecr`).
  - [x] `security-scan.sh` polls ECR scan results and fails the pipeline on any CRITICAL CVE (uses ECR's native scanner instead of Trivy — same finding source, no extra binary).
  - [x] Syft (Anchore) in the same step generates an SPDX-JSON SBOM as a CI artifact (`security scan` job, see `.gitlab-ci.yml`).
  - Acceptance: a known-vulnerable image fails the `🛡️ security scan` stage; `sbom.json` is downloadable from GitLab job artifacts (90-day retention).

- [x] **CloudFront security headers (webapp + website)**
  - [x] Custom `aws_cloudfront_response_headers_policy` baked into the shared `cloudfront-web` module (HSTS 1y + preload + includeSubDomains, X-Frame-Options DENY, X-Content-Type-Options nosniff, Referrer-Policy strict-origin-when-cross-origin, X-XSS-Protection 1; mode=block).
  - [x] CSP threaded in via `var.content_security_policy`. Webapp gets a permissive SPA CSP (`'unsafe-inline'` + `'unsafe-eval'` + Cognito + API origins). Website gets a tighter static-site CSP (no inline JS, no unsafe-eval).
  - Acceptance: `curl -I https://app.fcd8ayxqr72.com/` and `https://fcd8ayxqr72.com/` both return all six security headers; verified post-apply.

## Tier 4 — defer until justified by load / regulator

- [-] **Multi-region DR** — set up cross-region snapshot replication first; full DR is months of work, plan when audit pressure arrives.
- [-] **Service decomposition** — auth/account/account-holder are already separable Spring configs; split when scaling profiles diverge or team boundaries demand it.
- [-] **Spring Boot Actuator** — `/ready` is sufficient for now; revisit if we need richer health metrics.
