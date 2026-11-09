data "aws_caller_identity" "current" {}

data "aws_vpc" "primary" {
  tags = {
    Project     = var.application
    Environment = var.env
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.primary.id]
  }

  tags = {
    Project     = var.application
    Environment = var.env
    zone        = "private"
  }
}

# Master credentials are managed by RDS itself (manage_master_user_password = true
# in the rds module). The secret ARN is exposed by the RDS instance — we use that
# to look up the secret here, rather than relying on a fixed name (RDS-managed
# secrets are named `rds!db-...` with a generated suffix).
data "aws_secretsmanager_secret" "db_master" {
  arn = data.aws_db_instance.postgres.master_user_secret[0].secret_arn
}

# Per-integration secrets, written by their owning module (e.g. cognito-malai-api)
# or populated manually for third-party keys. Add one data source per integration.
data "aws_secretsmanager_secret" "cognito" {
  name = "${local.name_prefix}-malai-api-cognito"
}

# malai-api needs the user-pool ID for Cognito Admin* operations
# (CognitoAdminClient.addUserToGroup — see RegistrationOrchestrator). The
# cognito-malai-api secret doesn't carry the pool ID (it holds client_id /
# client_secret / issuer for SECRET_HASH flows), so we look the pool up by
# name — same pattern the seeder uses (ecs-malai-seeder/data.tf).
data "aws_cognito_user_pools" "malai" {
  name = "${local.name_prefix}-malai-api-user-pool"
}

# Phase 2 D-2.12: app-runtime DB credentials (malai_app role) — written by the
# rds module. Migrations run as the master role; the runtime DataSource uses
# malai_app, which has no UPDATE/DELETE on audit_events.
data "aws_secretsmanager_secret" "app_db" {
  name = "${local.name_prefix}-malai-api-app-db"
}

# Per-service Cognito service-token (client_credentials) creds. One secret per calling
# service, named `${prefix}-${service}-cognito-service` by cognito-malai-api/service-jwt.tf.
# Add a service to `local.services_requiring_service_jwt` (below) to wire its credentials
# into the malai-api task as `MAL_AWS_COGNITO_<SERVICE>_CLIENT_ID` etc.
# See malai-docs/webapp-flows/onboarding.md.
data "aws_secretsmanager_secret" "cognito_service" {
  for_each = local.services_requiring_service_jwt
  name     = "${local.name_prefix}-${each.key}-cognito-service"
}

data "aws_db_instance" "postgres" {
  db_instance_identifier = local.name_prefix
}

data "aws_security_group" "db" {
  name = "${local.name_prefix}-db"
}

# Owned by the kms module — encrypts the secret values we read at task start.
data "aws_kms_alias" "secrets" {
  name = "alias/${local.name_prefix}-secrets"
}

data "aws_kms_alias" "logs" {
  name = "alias/${local.name_prefix}-logs"
}
