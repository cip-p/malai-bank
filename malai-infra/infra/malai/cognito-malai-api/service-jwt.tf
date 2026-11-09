# Cognito service-to-service auth (OAuth2 client_credentials grant).
#
# Each entry in `services_requiring_service_jwt` gets:
#   - one Cognito M2M app client with its own client_id / client_secret
#   - one Secrets Manager secret holding those credentials + the token endpoint
#   - the OAuth2 scopes from `service_scopes[<service>]` (mutual exclusion: a service
#     can only get tokens for the scopes its app client whitelists)
#
# Receiving services validate inbound JWTs via the existing `ServiceJwtFilter` (same
# JWKS as user tokens) and check the scope is present. Today every internal caller
# (auth/account/payments) shares the single `malai-service` principal with the
# `onboarding.write` scope; if a future caller needs a tighter grant, add another
# entry below with its own scope set.
#
# References:
# - malai-docs/webapp-flows/onboarding.md (auth model)
# - malai-backend/.../security/SecurityConfig.kt (filter chain on internal paths)
# - malai-backend/.../security/ServiceJwtFilter.kt (scope enforcement)

locals {
  # Single source of truth for "which services need a service-token". Add new callers
  # here AND in `service_scopes` below; everything else (app client, secret, env-var
  # injection on the ECS task) follows automatically. The default `malai-service`
  # entry is the shared internal principal used by every backend service that calls
  # ledger / account-holder / account on `/api/v1/internal/...`.
  services_requiring_service_jwt = toset([
    "malai-service",
  ])

  # Per-service scope grants. Cognito enforces this at issue time: a service literally
  # cannot obtain a token for a scope its app client doesn't list, so receiving services
  # checking "the right scope is present" gives them mutual exclusion for free.
  service_scopes = {
    "malai-service" = ["${aws_cognito_resource_server.internal.identifier}/onboarding.write"]
  }

  service_token_endpoint_url = "https://${aws_cognito_user_pool_domain.this.domain}.auth.${var.region}.amazoncognito.com/oauth2/token"
}

# Cognito requires a user pool domain to expose OAuth2 endpoints (/oauth2/token, etc).
# `${prefix}-${env}-malai` is short and globally unique within Cognito's domain namespace.
resource "aws_cognito_user_pool_domain" "this" {
  domain       = "${local.name_prefix}-malai"
  user_pool_id = aws_cognito_user_pool.this.id
}

# A resource server is the namespace that owns custom OAuth2 scopes. Identifier matches
# the `malai-internal` prefix on the scope strings the receiving services check.
resource "aws_cognito_resource_server" "internal" {
  identifier   = "malai-internal"
  name         = "Malai internal services"
  user_pool_id = aws_cognito_user_pool.this.id

  scope {
    scope_name        = "onboarding.write"
    scope_description = "Stage and activate onboarding entities (account-holder, individual, address, account) and post book-transfer entries on the ledger, on behalf of malai's internal services."
  }
}

# One M2M app client per calling service. Distinct from the user-facing app client
# (`aws_cognito_user_pool_client.this` in main.tf): no user auth flows enabled, only
# the `client_credentials` OAuth flow, scoped per-service.
# (Cognito's console labels this style of client "Machine to machine".)
resource "aws_cognito_user_pool_client" "service" {
  for_each     = local.services_requiring_service_jwt
  name         = "${local.name_prefix}-${each.key}"
  user_pool_id = aws_cognito_user_pool.this.id

  generate_secret = true

  # No user-flow auth on these clients — they're strictly service-to-service.
  explicit_auth_flows = []

  # OAuth2 client_credentials grant only, scoped per service.
  allowed_oauth_flows                  = ["client_credentials"]
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_scopes                 = local.service_scopes[each.key]

  # No callback / logout URLs — there's no user-driven redirect flow here.
  supported_identity_providers = ["COGNITO"]

  depends_on = [aws_cognito_resource_server.internal]
}

# Secret naming: `${prefix}-${service}-cognito-service` makes it obvious whose
# credentials live in each entry. ecs-malai-api looks them up by this exact name.
resource "aws_secretsmanager_secret" "service" {
  for_each   = local.services_requiring_service_jwt
  name       = "${local.name_prefix}-${each.key}-cognito-service"
  kms_key_id = data.aws_kms_alias.secrets.target_key_arn
  tags       = local.tags
}

resource "aws_secretsmanager_secret_version" "service" {
  for_each  = local.services_requiring_service_jwt
  secret_id = aws_secretsmanager_secret.service[each.key].id
  secret_string = jsonencode({
    client_id          = aws_cognito_user_pool_client.service[each.key].id
    client_secret      = aws_cognito_user_pool_client.service[each.key].client_secret
    token_endpoint_url = local.service_token_endpoint_url
  })
}
