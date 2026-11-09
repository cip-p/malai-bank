locals {
  name_prefix = "${var.application}-${var.env}"

  # Plan 02-15 D-2.15 gated this to dev (`var.env == "dev"`) for Phase 2, with
  # Phase 5 lifting it to prod behind an explicit "WIPE PROD" confirmation.
  # Lifted on 2026-08-10 so the prod demo estate can be seeded at all — until
  # then prod had a working bank with an empty database.
  #
  # Still a `count` on a variable rather than an unconditional resource: the
  # indices keep dev's existing state addresses (`[0]`) stable, so this change
  # is a no-op replan in dev instead of a destroy-and-recreate of its IAM roles
  # and security group.
  #
  # What a prod run actually does: the container's default command is
  # `reset` (clean + migrate + seed). `clean` is surgical, not a wipe — every
  # DELETE is scoped through `users.email = <fixture email>`, so it removes the
  # seeded demo personas and their estates (Sarah, the customer-only user, ops
  # reviewer, manager, the banking-day cohort, the AML monitored customers) plus
  # their Cognito users, and nothing else. `audit_events`, `chain_head`,
  # `mdr_entries`, the system/SUSPENSE accounts and any non-seeded row are left
  # intact by design — see CleanCommand's kdoc in malai-backend, which records
  # that an earlier TRUNCATE-everything version was rejected as too coarse.
  #
  # So a re-seed re-baselines the demo estate; it does not destroy an
  # evaluator's self-registered account, which carries an email outside the
  # fixture set. There is no ECS service here either — nothing runs unless
  # someone calls run-task.
  #
  # Still worth routing through malai-backend's `demo-reset.sh` rather than
  # calling run-task directly, since that is where the env guard lives. It no
  # longer refuses non-dev environments — that refusal became a type-the-
  # environment-name confirmation on 2026-08-10.
  seeder_enabled = var.deploy_seeder

  # Same derivation as ecs-malai-api's local of the same name — the seeder is
  # its own Fargate task, so localhost is never the stub for it either.
  malai_stub_base_url = coalesce(
    var.malai_stub_base_url,
    "http://malai-stub.${var.application}-${var.env}.malai.internal:${var.malai_stub_container_port}",
  )

  image_uri = "${var.malai_seeder_ecr_shared_account_id}.dkr.ecr.${var.region}.amazonaws.com/${var.malai_seeder_ecr_repository_name}:${var.malai_seeder_initial_image_tag}"

  tags = {
    Environment = var.env
    Project     = var.application
    Terraform   = true
    infra-unit  = "ecs-malai-seeder"
  }
}

# Reuse the malai-api ECS cluster so the seeder task lands in the same Fargate
# capacity as the live service. The cluster name is conventional
# (${name_prefix}-malai-api), but a data lookup makes the dependency explicit.
data "aws_ecs_cluster" "malai_api" {
  count        = local.seeder_enabled ? 1 : 0
  cluster_name = "${local.name_prefix}-malai-api"
}

resource "aws_cloudwatch_log_group" "task" {
  count             = local.seeder_enabled ? 1 : 0
  name              = "/aws/ecs/${local.name_prefix}-malai-seeder"
  retention_in_days = var.malai_seeder_log_retention_days
  kms_key_id        = data.aws_kms_alias.logs.target_key_arn
  tags              = local.tags
}

#
# Security group — dedicated to the seeder task so the RDS ingress rule is
# clearly attributable. Egress is wide-open (Fargate needs to reach ECR,
# Secrets Manager, Cognito, and the OTel/CloudWatch endpoints).
#
resource "aws_security_group" "task" {
  count       = local.seeder_enabled ? 1 : 0
  name        = "${local.name_prefix}-malai-seeder-task"
  description = "Fargate one-shot tasks for malai-seeder (Plan 02-15)."
  vpc_id      = data.aws_vpc.primary.id

  tags = local.tags
}

resource "aws_security_group_rule" "task_egress" {
  count             = local.seeder_enabled ? 1 : 0
  type              = "egress"
  security_group_id = aws_security_group.task[0].id
  protocol          = "-1"
  from_port         = 0
  to_port           = 0
  cidr_blocks       = ["0.0.0.0/0"]
}

# Owned here so the rds module stays runtime-agnostic. Same pattern as
# ecs-malai-api's `db_ingress_from_task`.
resource "aws_security_group_rule" "db_ingress_from_seeder" {
  count                    = local.seeder_enabled ? 1 : 0
  type                     = "ingress"
  security_group_id        = data.aws_security_group.db.id
  source_security_group_id = aws_security_group.task[0].id
  protocol                 = "tcp"
  from_port                = data.aws_db_instance.postgres.port
  to_port                  = data.aws_db_instance.postgres.port
  description              = "malai-seeder Fargate tasks"
}

# The seeder's route to malai-stub (needed by the card-PAN registration at the
# end of `seed`) is not here: ecs-malai-stub generates the rules in both
# directions for every unit named in its `malai_stub_consumers` list, of which
# this is one.
#
#
# IAM
#
data "aws_iam_policy_document" "task_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  count              = local.seeder_enabled ? 1 : 0
  name               = "${local.name_prefix}-malai-seeder-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json
  tags               = local.tags
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  count      = local.seeder_enabled ? 1 : 0
  role       = aws_iam_role.execution[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_inline" {
  statement {
    sid    = "SharedEcrPull"
    effect = "Allow"
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
    ]
    resources = ["*"]
  }

  statement {
    sid     = "ReadAppSecrets"
    effect  = "Allow"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      data.aws_secretsmanager_secret.db_master.arn,
      data.aws_secretsmanager_secret.app_db.arn,
    ]
  }

  statement {
    sid       = "DecryptAppSecrets"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.secrets.target_key_arn]
  }
}

resource "aws_iam_role_policy" "execution_inline" {
  count  = local.seeder_enabled ? 1 : 0
  name   = "${local.name_prefix}-malai-seeder-ecs-execution-inline"
  role   = aws_iam_role.execution[0].id
  policy = data.aws_iam_policy_document.execution_inline.json
}

resource "aws_iam_role" "task" {
  count              = local.seeder_enabled ? 1 : 0
  name               = "${local.name_prefix}-malai-seeder-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json
  tags               = local.tags
}

# Cognito Admin API permissions — exactly the four calls SeederCognitoAdmin uses.
# Scoped down from ecs-malai-api's broader baseline because the seeder doesn't
# need GetUser / InitiateAuth / ForgotPassword / etc.
data "aws_iam_policy_document" "task_inline" {
  statement {
    sid    = "CognitoAdminForSeeder"
    effect = "Allow"
    actions = [
      "cognito-idp:AdminCreateUser",
      "cognito-idp:AdminSetUserPassword",
      "cognito-idp:AdminAddUserToGroup",
      "cognito-idp:AdminDeleteUser",
      "cognito-idp:DescribeUserPool",
      "cognito-idp:DescribeUserPoolClient",
    ]
    resources = [
      "arn:aws:cognito-idp:${var.region}:${data.aws_caller_identity.current.account_id}:userpool/*",
    ]
  }

  # CloudWatch Logs put — the application logs to its container's awslogs
  # driver, but if seeder code ever uses the AWS SDK to log directly (or
  # creates a log group on the fly) this keeps it from being silently dropped.
  statement {
    sid    = "CloudWatchLogs"
    effect = "Allow"
    actions = [
      "logs:PutLogEvents",
      "logs:CreateLogStream",
      "logs:DescribeLogStreams",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "task_inline" {
  count  = local.seeder_enabled ? 1 : 0
  name   = "${local.name_prefix}-malai-seeder-ecs-task-inline"
  role   = aws_iam_role.task[0].id
  policy = data.aws_iam_policy_document.task_inline.json
}

#
# One-shot task definition. NO service block — operators trigger runs via
# `malai-backend/demo-reset.sh` which calls `aws ecs run-task`.
#
resource "aws_ecs_task_definition" "this" {
  count                    = local.seeder_enabled ? 1 : 0
  family                   = "${local.name_prefix}-malai-seeder"
  cpu                      = "512"
  memory                   = "1024"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = aws_iam_role.execution[0].arn
  task_role_arn            = aws_iam_role.task[0].arn

  # Match malai-api's Graviton2 setup so the multi-arch image manifest serves
  # the ARM64 variant — keeps cost in line and ECR doesn't need a separate
  # amd64-only build.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([{
    name      = "malai-seeder"
    image     = local.image_uri
    essential = true
    # Default `reset` (clean + migrate + seed). Override with `verify-chain`
    # via `aws ecs run-task --overrides '{"containerOverrides":[{"name":"malai-seeder","command":["verify-chain"]}]}'`.
    #
    # These are picocli SUBCOMMANDS, so they carry no leading dashes. This read
    # `--reset` until 2026-08-12, which picocli rejects as an unknown option —
    # a console run of this task definition failed on its own default. It went
    # unnoticed because demo-reset.sh always passes a containerOverride, and
    # its `CMD` default was the correct bare `reset`.
    command = ["reset"]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.env },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${data.aws_db_instance.postgres.endpoint}/${data.aws_db_instance.postgres.db_name}?sslmode=require" },
      { name = "MAL_BANK_SORT_CODE", value = var.bank_sort_code },
      { name = "MAL_BANK_BIC", value = var.bank_bic },
      { name = "AWS_REGION", value = var.region },
      # Not a loopback: malai-stub is its own task, reached over Cloud Map.
      # Without this the seeder falls back to http://localhost:9090 (see
      # malai-seeder's application.properties), where nothing listens, and the
      # closing "register the card PANs with the stub scheme" step of `seed`
      # fails connection-refused on every run. It is fail-open, so the seed
      # still reports success — the only symptom is that ambient card spend
      # never targets the seeded cards. ecs-malai-api gained this variable for
      # the same reason; the seeder was missed.
      { name = "MAL_STUB_BASE_URL", value = local.malai_stub_base_url },
      # Pulled straight from the Cognito user-pool lookup so cognito-malai-api's
      # secret shape (client_id / client_secret / issuer) doesn't have to change.
      { name = "MAL_AWS_COGNITO_USER_POOL_ID", value = tolist(data.aws_cognito_user_pools.malai.ids)[0] },
    ]
    secrets = [
      { name = "MAL_DB_MASTER_USERNAME", valueFrom = "${data.aws_secretsmanager_secret.db_master.arn}:username::" },
      { name = "MAL_DB_MASTER_PASSWORD", valueFrom = "${data.aws_secretsmanager_secret.db_master.arn}:password::" },
      { name = "MAL_APP_DB_USERNAME", valueFrom = "${data.aws_secretsmanager_secret.app_db.arn}:username::" },
      { name = "MAL_APP_DB_PASSWORD", valueFrom = "${data.aws_secretsmanager_secret.app_db.arn}:password::" },
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.task[0].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "malai-seeder"
      }
    }
  }])

  tags = local.tags
}
