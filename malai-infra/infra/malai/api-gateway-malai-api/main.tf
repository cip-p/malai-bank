locals {
  name_prefix = "${var.application}-${var.env}"

  # The app client that issues user tokens — see the selection rationale in
  # data.tf. Named lookup, not `client_ids[0]`.
  malai_api_user_pool_client_id = one([
    for client in data.aws_cognito_user_pool_client.all : client.id
    if client.name == "${var.application}-${var.env}-malai-api-user-pool-client"
  ])
  tags = {
    Environment = var.env
    Terraform   = true
    infra-unit  = "api-gateway-malai-api"
    Apps        = join(" ", [var.app_mal, var.app_malai_api])
  }

  # Endpoints intended to need no credential — register, login, the OTP
  # verification step, the leftover password-reset paths, and the public probes.
  #
  # This list stopped governing authorisation on 2026-08-10, when the JWT
  # authorizer was removed (see the long note further down). The gateway now
  # forwards everything and the application decides, so a path missing from
  # here is no longer "treated as authenticated" — it is simply forwarded like
  # everything else, and `JwtDecoderFilter` rejects it if it needs a token.
  # The list is retained as a statement of intent and as the shape the
  # allowlist would take if an authorizer that can read cookies ever exists.
  #
  # `OPTIONS /{proxy+}` still earns its place: it keeps CORS preflights matching
  # an explicit route, so API Gateway's auto-CORS handler answers them directly
  # from `cors_configuration` without invoking the integration at all.
  public_routes = toset([
    "OPTIONS /{proxy+}",
    "GET /version",
    # /openapi.yaml is the hand-authored cross-repo contract (Plan 02-16,
    # BACK-09). The webapp + any first-party CLI generate clients from it
    # and `oasdiff` polls it from CI. Public so partners can fetch the
    # contract without a JWT.
    "GET /openapi.yaml",
    "POST /api/v1/auth/register",
    "POST /api/v1/auth/login",
    "POST /api/v1/auth/refresh-token",

    # The passwordless (EMAIL_OTP) second step: register and login both return
    # "a code has been sent", and the caller posts the code back here. It has
    # to be public for the same reason register and login are — the caller has
    # no JWT yet; the whole point of this call is to get one.
    #
    # Missing from this list until 2026-08-10, which made every registration
    # fail at the second step with a 401 from the JWT authorizer, never
    # reaching the app. Nothing in the app logs, because the request died at
    # the gateway. If a public auth endpoint ever returns 401 with no
    # corresponding app log line, look here first.
    "POST /api/v1/auth/verify-code",

    # Below are the password-flow endpoints. The backend removed them from
    # openapi.yaml in the passwordless cutover (auth-service ADR-0001), so
    # these entries are dead — they allowlist paths the app no longer serves,
    # and a call to any of them 404s rather than 401s. They are left in place
    # deliberately rather than tidied away: password auth itself is still
    # enabled (the seeded demo user keeps a password), and removing them is
    # the kind of "finishing the cutover" that this project has explicitly
    # decided against. Delete them only alongside a decision to retire the
    # password path properly.
    "POST /api/v1/auth/confirm-register",
    "POST /api/v1/auth/resend-confirmation-code",
    "POST /api/v1/auth/forgot-password",
    "POST /api/v1/auth/confirm-forgot-password",
  ])
}

#
# HTTP API v2.
#
# We migrated off REST v1 + custom Lambda authorizer + opaque-token-in-Postgres
# pattern. The new flow:
#
#   client → Cognito JWT (HttpOnly `access_token` cookie, or Bearer header)
#          → API Gateway HTTP API v2 (forwards; no authorizer — see below)
#          → VPC Link v2 → Cloud Map → ECS task
#          → JwtDecoderFilter verifies the token
#
# What this gives us versus the old setup:
#   - No Lambda authorizer cold start on auth (JWT verification is in-process)
#   - No `jwt_tokens` Postgres table
#   - No NLB SG enforcement gotcha (VPC Link v2 uses standard SGs)
#   - REGIONAL custom domain (AWS-managed CloudFront in the EDGE path is gone)
#   - No load balancer in the path at all (see the integration below)
#
resource "aws_apigatewayv2_api" "this" {
  name          = "${local.name_prefix}-malai-api-gateway"
  description   = "Malai API — HTTP API v2"
  protocol_type = "HTTP"

  # Built-in CORS handling. OPTIONS preflight responses are emitted by API
  # Gateway directly, no Spring code needed for that.
  #
  # `allow_credentials` must be true because auth is cookie-based: the webapp
  # fetches with `credentials: 'include'`, and a browser discards any response
  # whose `Access-Control-Allow-Credentials` is not exactly `true`. When this
  # block is present API Gateway owns the CORS headers outright and replaces
  # whatever the integration returned, so Spring's own `allowCredentials = true`
  # in SecurityConfig never reaches the browser — it has to be set here.
  #
  # `expose_headers` is deliberately absent rather than `["*"]`: a wildcard is
  # invalid in a credentialed response, and the webapp reads no response headers.
  #
  # `allow_headers` must list every header the browser actually sends, and a
  # missing one fails in a way that does not look like CORS configuration at
  # all: API Gateway answers the preflight 200 but emits *no* CORS headers, so
  # the browser reports a generic CORS error with no clue which header caused
  # it. `Idempotency-Key` was missing until 2026-08-11, which broke every write
  # path in the webapp from the browser — freeze/unfreeze/replace card, credit
  # card application, create payee, create standing order, the ops case-close
  # and SAR-draft actions, and the sandbox inbound payment — 14 call sites, all
  # of them presenting as "CORS error" rather than as a rejected header.
  #
  # It is a cross-cutting invariant that production write endpoints take
  # `Idempotency-Key`, so anything added to that set is already covered here.
  # Adding a *new* custom header to a request is the thing that needs this list
  # updated in the same change.
  cors_configuration {
    allow_origins     = var.cors_allowed_origins
    allow_methods     = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers     = ["Content-Type", "Authorization", "MAL-Account-Holder-Id", "Idempotency-Key"]
    allow_credentials = true
    max_age           = 3600
  }

  tags = local.tags
}

#
# There is deliberately no authorizer on this API. Authentication is enforced
# in the application, by `JwtDecoderFilter` in malai-commons/malai-security.
#
# A native JWT authorizer lived here until 2026-08-10 and had to go, because it
# is structurally incompatible with how this product authenticates. Auth is
# cookie-based: `auth-service` sets an HttpOnly `access_token` cookie and the
# webapp calls the API with credentials-included fetch. An HTTP API JWT
# authorizer can only take its identity source from a header or a query string
# — `$request.cookie.*` is not a supported identity source — so it never saw
# the token the browser was sending and rejected every authenticated call with
# a bare `{"message":"Unauthorized"}` before the request reached the app.
# HttpOnly is the point of the cookie, so "send it as a header instead" would
# mean letting JavaScript read the session token.
#
# The two alternatives were both worse. A Lambda authorizer can read
# `$request.header.cookie`, but reintroduces exactly the per-auth cold start
# that the REST v1 → HTTP API v2 migration was done to remove. Dropping
# HttpOnly to satisfy the authorizer trades a real security property for an
# edge check we already duplicate.
#
# What this costs: unauthenticated requests now reach Fargate rather than dying
# at the gateway. `JwtDecoderFilter` rejects them immediately, so the exposure
# is wasted compute on junk traffic, not access. If that ever matters, the
# answer is the WAF rate-limit rule (`enable_waf`), not an authorizer that
# cannot read the credential this app uses.
#
# `JwtDecoderFilter` reads the cookie first and the `Authorization: Bearer`
# header second, so browsers, CLIs and mobile clients all still work.
#

#
# VPC Link v2 — connects the HTTP API to the malai-api Fargate tasks.
# Distinct from REST v1 VPC Links: lives directly in customer subnets, uses
# standard SGs (no `enforce_security_group_inbound_rules_on_private_link_traffic`).
#
# The link used to terminate on an internal ALB on port 80. That ALB is gone
# (~$17/mo for a single-task service); the link now reaches task IPs directly
# via Cloud Map, so both SG rules below are scoped to the container port
# instead.
#
resource "aws_security_group" "vpc_link" {
  name        = "${local.name_prefix}-malai-api-vpc-link"
  description = "VPC Link v2 ENIs for malai-api HTTP API"
  vpc_id      = data.aws_vpc.primary.id

  tags = local.tags
}

resource "aws_security_group_rule" "vpc_link_egress_to_task" {
  type                     = "egress"
  security_group_id        = aws_security_group.vpc_link.id
  source_security_group_id = data.aws_security_group.task.id
  protocol                 = "tcp"
  from_port                = var.malai_api_container_port
  to_port                  = var.malai_api_container_port
  description              = "VPC Link to malai-api Fargate tasks"
}

# Ingress rule on the task SG (owned in ecs-malai-api) — granted from this
# module so the compute module stays consumer-agnostic. Same pattern as the DB
# SG ingress in ecs-malai-api/main.tf.
resource "aws_security_group_rule" "task_ingress_from_vpc_link" {
  type                     = "ingress"
  security_group_id        = data.aws_security_group.task.id
  source_security_group_id = aws_security_group.vpc_link.id
  protocol                 = "tcp"
  from_port                = var.malai_api_container_port
  to_port                  = var.malai_api_container_port
  description              = "API Gateway HTTP v2 VPC Link"
}

resource "aws_apigatewayv2_vpc_link" "this" {
  name               = "${local.name_prefix}-malai-api"
  subnet_ids         = data.aws_subnets.private.ids
  security_group_ids = [aws_security_group.vpc_link.id]

  tags = local.tags
}

#
# Single integration: forward to the Cloud Map service via the VPC link. Both
# the `$default` (authenticated) route and the public-route allowlist target
# this same integration — they only differ in whether they require auth.
#
# `integration_uri` is a Cloud Map service ARN rather than an ALB listener ARN.
# API Gateway resolves the service, reads `AWS_INSTANCE_IPV4` +
# `AWS_INSTANCE_PORT` off each healthy instance, and load-balances across them
# itself — which is why the SRV record type on the Cloud Map service (see
# ecs-malai-api) is required rather than optional.
#
resource "aws_apigatewayv2_integration" "malai_api" {
  api_id                 = aws_apigatewayv2_api.this.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = data.aws_service_discovery_service.malai_api.arn
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.this.id
  payload_format_version = "1.0"
  timeout_milliseconds   = 29000
}

# Catch-all route. No `authorization_type` — see the note above the (removed)
# authorizer: every request is forwarded, and the application decides.
resource "aws_apigatewayv2_route" "default" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "$default"
  target    = "integrations/${aws_apigatewayv2_integration.malai_api.id}"
}

# The explicit route list below is no longer load-bearing for authorisation —
# with no authorizer on `$default`, these resolve to the same unauthenticated
# forward the catch-all already performs. It is kept for two reasons: it
# documents which endpoints are *intended* to need no credential (the app still
# enforces that distinction), and `OPTIONS /{proxy+}` keeps CORS preflight
# matching an explicit route rather than depending on `$default` behaviour.
#
# HTTP API v2 picks the most specific match, so these still win over `$default`.
resource "aws_apigatewayv2_route" "public" {
  for_each = local.public_routes

  api_id    = aws_apigatewayv2_api.this.id
  route_key = each.key
  target    = "integrations/${aws_apigatewayv2_integration.malai_api.id}"
  # No authorization_type → NONE, same as $default now.
}

#
# Stage. `$default` is the catch-all stage name for HTTP APIs (no path prefix
# in the URL — `https://api.app.example.com/api/v1/...` rather than `/v1/...`).
# `auto_deploy = true` removes the explicit deployment dance REST v1 needed.
#
resource "aws_cloudwatch_log_group" "access_log_group" {
  name              = "/aws/api-gateway-v2/${local.name_prefix}-malai-api"
  retention_in_days = var.api_gw_log_retention
  kms_key_id        = data.aws_kms_alias.logs.target_key_arn

  tags = local.tags
}

resource "aws_apigatewayv2_stage" "this" {
  api_id = aws_apigatewayv2_api.this.id
  # `$default` is the unnamed catch-all stage — its URL has no path prefix, so
  # the regional execute-api endpoint is `https://{id}.execute-api.{region}.amazonaws.com/<route>`
  # rather than `.../v1/<route>`. CloudFront can forward `/version` straight
  # through and Spring sees `/version` (not `/v1/version`).
  #
  # The earlier WAF blocker on `$default` was specific to WAFv2 associating
  # *directly* with the stage's ARN (`$` choked AWS's parser). With the
  # WebACL now on CloudFront — not on the stage — that doesn't apply.
  name        = "$default"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access_log_group.arn
    format          = file("${path.module}/templates/log_format.json")
  }

  default_route_settings {
    detailed_metrics_enabled = true
    throttling_rate_limit    = 1000
    throttling_burst_limit   = 2000
  }

  tags = local.tags
}
