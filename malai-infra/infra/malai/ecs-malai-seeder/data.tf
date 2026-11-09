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

data "aws_db_instance" "postgres" {
  db_instance_identifier = "${var.application}-${var.env}"
}

data "aws_security_group" "db" {
  name = "${var.application}-${var.env}-db"
}

# RDS-managed master credentials. The seeder runs CleanCommand + MigrateCommand
# (and SeedCommand) under this role so it has TRUNCATE on audit_events + the
# CREATE ROLE / GRANT permission V12+ migrations need.
data "aws_secretsmanager_secret" "db_master" {
  arn = data.aws_db_instance.postgres.master_user_secret[0].secret_arn
}

# Runtime app role credentials — only used for the V12 placeholder
# `${malai_app_password}` when the seeder runs MigrateCommand against a fresh DB.
data "aws_secretsmanager_secret" "app_db" {
  name = "${var.application}-${var.env}-malai-api-app-db"
}

# Cognito Admin API target — same user pool the malai-api reads. The seeder
# only needs the user-pool ID (Admin* calls don't use SECRET_HASH, so no
# client_secret to wire). Looking it up by name avoids touching the
# cognito-malai-api secret shape (which today carries client_id / client_secret
# / issuer — no user_pool_id key).
data "aws_cognito_user_pools" "malai" {
  name = "${var.application}-${var.env}-malai-api-user-pool"
}

data "aws_kms_alias" "secrets" {
  name = "alias/${var.application}-${var.env}-secrets"
}

data "aws_kms_alias" "logs" {
  name = "alias/${var.application}-${var.env}-logs"
}
