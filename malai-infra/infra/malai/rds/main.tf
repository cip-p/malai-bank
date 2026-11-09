locals {
  name_prefix = "${var.application}-${var.env}"
  identifier  = "${var.application}-${var.env}"

  tags = {
    Environment = var.env
    Project     = var.application
    Terraform   = true
    infra-unit  = "rds"
    Apps        = var.app_mal
  }
}


# RDS itself manages the master user password in Secrets Manager — see
# `aws_db_instance.postgres.manage_master_user_password` below. AWS generates the
# password, stores it in a generated secret (`rds!db-...`), encrypts it with the
# CMK we pass, and rotates it on a default schedule (7 days). No custom rotation
# lambda or SAR app needed.
#
# Consumers (ecs-malai-api) read the secret ARN via
# `data.aws_db_instance.postgres.master_user_secret[0].secret_arn`
# rather than looking it up by a fixed name.

#
# Storage encryption uses the AWS-managed `aws/rds` key rather than a
# customer-managed one. There used to be a dedicated CMK here; it cost $1/mo
# to provide, for this database, nothing a real bank would recognise as key
# control — no separate key policy, no independent revocation, no BYOK. The
# data is a seeded demo estate.
#
# The volume is still encrypted at rest. What the AWS-managed key gives up is
# the ability to write your own key policy, to revoke access by disabling the
# key, and to audit key usage separately from RDS usage. Recreate the CMK
# below and point `kms_key_id` back at it if any of those become real
# requirements:
#
#   resource "aws_kms_key" "rds" {
#     description             = "${local.name_prefix}-db encryption"
#     deletion_window_in_days = 7
#     enable_key_rotation     = true
#     tags                    = local.tags
#   }
#
# `kms_key_id` is ForceNew on aws_db_instance — RDS cannot re-key a live
# instance, only snapshot-and-restore. This change was free only because
# neither env had an instance deployed when it was made (both were torn down
# in the 2026-06-14 cost-pause). Switching back on a running database is a
# migration, not an apply.
#

# Subnet group spans all 3 private subnets so we can flip rds_multi_az to true
# without recreating the DB (AWS requires the group to cover >=2 AZs for multi-AZ).
resource "aws_db_subnet_group" "this" {
  name        = "${local.name_prefix}-db"
  description = "Subnet group for ${local.identifier}"
  subnet_ids  = data.aws_subnets.private.ids

  tags = local.tags
}

resource "aws_security_group" "postgres" {
  name        = "${local.name_prefix}-db"
  description = "Security group for ${local.identifier}"
  vpc_id      = data.aws_vpc.primary.id

  tags = local.tags
}

# Ingress is granted via separate aws_security_group_rule resources from other modules
# (e.g. the ECS task security group). We only allow egress here.
resource "aws_security_group_rule" "postgres_egress" {
  type              = "egress"
  security_group_id = aws_security_group.postgres.id
  protocol          = "-1"
  from_port         = 0
  to_port           = 0
  cidr_blocks       = ["0.0.0.0/0"]
}

resource "aws_db_parameter_group" "this" {
  name        = "${local.name_prefix}-db"
  family      = "postgres17"
  description = "Parameter group for ${local.identifier}"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  parameter {
    name  = "log_statement"
    value = "ddl"
  }

  parameter {
    name  = "pg_stat_statements.track"
    value = "all"
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = local.tags
}

resource "aws_db_instance" "postgres" {
  identifier     = local.identifier
  engine         = "postgres"
  engine_version = var.rds_engine_version
  instance_class = var.rds_instance_class

  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  storage_type          = var.rds_storage_type
  storage_encrypted     = true

  db_name  = var.rds_database_name
  username = var.rds_master_username
  port     = var.rds_port

  # RDS manages the master password in Secrets Manager and rotates it on a
  # default schedule. The secret is encrypted with our `secrets` CMK. The ARN
  # is exposed via `aws_db_instance.master_user_secret[0].secret_arn`, which
  # consumers read through the `aws_db_instance` data source.
  manage_master_user_password   = true
  master_user_secret_kms_key_id = data.aws_kms_alias.secrets.target_key_arn

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.postgres.id]
  publicly_accessible    = false
  multi_az               = var.rds_multi_az

  parameter_group_name = aws_db_parameter_group.this.name

  backup_retention_period = var.rds_backup_retention_days
  backup_window           = var.rds_backup_window
  maintenance_window      = var.rds_maintenance_window

  performance_insights_enabled    = true
  enabled_cloudwatch_logs_exports = ["postgresql"]

  skip_final_snapshot       = var.rds_skip_final_snapshot
  final_snapshot_identifier = "${local.identifier}-final"
  deletion_protection       = !var.rds_skip_final_snapshot
  apply_immediately         = var.rds_apply_immediately

  tags = local.tags
}

# Phase 2 D-2.12: app-runtime Postgres role secret.
# The `malai_app` role is created by V12 Flyway migration with INSERT, SELECT
# only on `audit_events` — append-only enforced at the DB layer. Migrations
# run as the master role; the application's runtime DataSource uses
# `malai_app`. Both ecs-malai-api (runtime) and ecs-malai-seeder (Plan 02-15)
# read this same secret.
resource "random_password" "app_db" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>?"
}

resource "aws_secretsmanager_secret" "app_db" {
  name       = "${local.name_prefix}-malai-api-app-db"
  kms_key_id = data.aws_kms_alias.secrets.target_key_arn
  tags       = local.tags
}

resource "aws_secretsmanager_secret_version" "app_db" {
  secret_id = aws_secretsmanager_secret.app_db.id
  secret_string = jsonencode({
    username = "malai_app"
    password = random_password.app_db.result
  })
}
