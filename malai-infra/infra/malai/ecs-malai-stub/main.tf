locals {
  name_prefix = "${var.application}-${var.env}"

  image_uri = "${var.malai_stub_ecr_shared_account_id}.dkr.ecr.${var.region}.amazonaws.com/${var.malai_stub_ecr_repository_name}:${var.malai_stub_initial_image_tag}"

  capacity_provider = var.malai_stub_use_spot ? "FARGATE_SPOT" : "FARGATE"

  # Declared consumers that actually exist in this environment, name => task
  # security group id. The length check drops names whose unit is not deployed
  # here; one() then unwraps the single match, and would fail loudly if the
  # naming convention ever matched two groups.
  consumer_task_security_groups = {
    for name, sg in data.aws_security_groups.consumer :
    name => one(sg.ids) if length(sg.ids) == 1
  }

  # In-VPC address malai-api and the stub use to reach each other. Both sides
  # default to localhost in application config, which is right for a developer
  # running both processes and wrong for anything deployed.
  stub_base_url = "http://malai-stub.${local.name_prefix}.malai.internal:${var.malai_stub_container_port}"

  # Derived per environment rather than defaulted to a literal, so dev resolves
  # mal-dev.malai.internal and prod resolves mal-prod.malai.internal.
  malai_api_base_url = coalesce(
    var.malai_api_internal_base_url,
    "http://malai-api.${local.name_prefix}.malai.internal:${var.malai_api_container_port}",
  )

  # Readiness probe. The stub's base image is temurin *alpine*, so unlike
  # malai-api there is no bash and no /dev/tcp — but busybox wget is present,
  # which is the simpler tool for the job. `--spider` makes a request without
  # writing the body anywhere; a non-2xx exits non-zero.
  #
  # Not /actuator/health: spring-boot-starter-actuator is not on malai-stub's
  # classpath, so that path 404s and would fail every task. This endpoint reads
  # the loaded fictional-merchant directory, so a 200 proves both that Spring
  # finished booting and that the stub's config imports resolved — a stronger
  # signal than a liveness ping.
  readiness_probe = "wget --spider -q http://127.0.0.1:${var.malai_stub_container_port}/scenarios/cards/merchants || exit 1"

  tags = {
    Environment = var.env
    Project     = var.application
    Terraform   = true
    infra-unit  = "ecs-malai-stub"
    Apps        = join(" ", [var.app_mal, "malai-stub"])
  }
}

resource "aws_cloudwatch_log_group" "task" {
  name              = "/aws/ecs/${local.name_prefix}-malai-stub"
  retention_in_days = var.malai_stub_log_retention_days
  kms_key_id        = data.aws_kms_alias.logs.target_key_arn

  tags = local.tags
}

#
# Security group.
#
# Ingress is from the malai-api task SG only. The stub simulates other banks
# and card schemes — it must not be reachable from the internet, and nothing
# outside this VPC has any business calling it. There is deliberately no load
# balancer, no public IP and no API Gateway route.
#
resource "aws_security_group" "task" {
  name        = "${local.name_prefix}-malai-stub-task"
  description = "Fargate tasks for malai-stub"
  vpc_id      = data.aws_vpc.primary.id

  tags = local.tags
}

# Both directions for every unit in var.malai_stub_consumers, generated from
# that one list so the pair cannot drift apart. See the variable's description
# for why this is generated rather than hand-written per consumer.
resource "aws_security_group_rule" "task_ingress_from_consumer" {
  for_each                 = local.consumer_task_security_groups
  type                     = "ingress"
  security_group_id        = aws_security_group.task.id
  source_security_group_id = each.value
  protocol                 = "tcp"
  from_port                = var.malai_stub_container_port
  to_port                  = var.malai_stub_container_port
  description              = "${each.key} to malai-stub"
}

moved {
  from = aws_security_group_rule.task_ingress_from_malai_api
  to   = aws_security_group_rule.task_ingress_from_consumer["malai-api"]
}

# The stub calls back into malai-api's gateways, so it needs egress to that SG
# as well as general outbound for ECR pulls and CloudWatch.
resource "aws_security_group_rule" "task_egress" {
  type              = "egress"
  security_group_id = aws_security_group.task.id
  protocol          = "-1"
  from_port         = 0
  to_port           = 0
  cidr_blocks       = ["0.0.0.0/0"]
}

# The reverse direction: each consumer must be allowed out to the stub. Owned
# here rather than in the consuming units so they stay unaware of the stub, and
# so destroying this one takes its own rules with it.
#
# Redundant for a consumer whose egress is already wide open (the seeder's is),
# but harmless — security group rules are permissive, so an explicit rule
# alongside a broad one costs nothing and keeps the pair symmetrical for the
# consumers whose egress is restricted, as malai-api's is.
resource "aws_security_group_rule" "consumer_egress_to_task" {
  for_each                 = local.consumer_task_security_groups
  type                     = "egress"
  security_group_id        = each.value
  source_security_group_id = aws_security_group.task.id
  protocol                 = "tcp"
  from_port                = var.malai_stub_container_port
  to_port                  = var.malai_stub_container_port
  description              = "${each.key} to malai-stub"
}

moved {
  from = aws_security_group_rule.malai_api_egress_to_stub
  to   = aws_security_group_rule.consumer_egress_to_task["malai-api"]
}

# The callback direction, which is easy to forget because the stub looks like a
# leaf. It is not: the scheme simulations call *back* into malai-api's FPS /
# SWIFT / BACS / card gateway endpoints to deliver settlement and inbound
# events. Without this rule the stub resolves malai-api and then hangs until
# the connection times out, which surfaces as an outbound payment holding funds
# forever rather than as anything that looks like a network fault.
#
# malai-api's SG otherwise only admits the API Gateway VPC Link.
resource "aws_security_group_rule" "malai_api_ingress_from_stub" {
  type                     = "ingress"
  security_group_id        = data.aws_security_group.malai_api_task.id
  source_security_group_id = aws_security_group.task.id
  protocol                 = "tcp"
  from_port                = var.malai_api_container_port
  to_port                  = var.malai_api_container_port
  description              = "malai-stub scheme callbacks into malai-api"
}

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
  name               = "${local.name_prefix}-malai-stub-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Cross-account pull from the shared ECR registry, plus the KMS grant the
# encrypted log group needs.
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
}

resource "aws_iam_role_policy" "execution_inline" {
  name   = "${local.name_prefix}-malai-stub-ecs-execution-inline"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_inline.json
}

resource "aws_iam_role" "task" {
  name               = "${local.name_prefix}-malai-stub-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json

  tags = local.tags
}

#
# SQS. The stub is not only an HTTP fake — it is the other side of the rails,
# consuming scheme commands and publishing scheme events over the same FIFO
# queues malai-api uses. Without this it does not merely lose a feature: the
# spring-cloud-aws SqsAsyncClient is autoconfigured at startup and the process
# exits during bean creation, so the task crash-loops.
#
# Scoped to this account's queues, and deliberately the same action list as
# ecs-malai-api rather than a trimmed one.
#
# `sqs:CreateQueue` in particular is load-bearing: malai-sqs' QueueProvisioner
# runs during `afterSingletonsInstantiated` and creates all 13 FIFO queues at
# startup. An earlier version of this policy dropped CreateQueue on the
# assumption the stub only consumes and produces on queues someone else owns;
# the task crash-looped with `not authorized to perform: sqs:CreateQueue`
# before it ever served a request. The stub sits on the opposite end of the
# same rails as malai-api and provisions them identically, so its grant should
# match rather than diverge.
#
data "aws_iam_policy_document" "task_inline" {
  statement {
    sid    = "SqsQueueAccess"
    effect = "Allow"
    actions = [
      "sqs:CreateQueue",
      "sqs:GetQueueUrl",
      "sqs:GetQueueAttributes",
      "sqs:SetQueueAttributes",
      "sqs:SendMessage",
      "sqs:SendMessageBatch",
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:DeleteMessageBatch",
      "sqs:ChangeMessageVisibility",
      "sqs:ChangeMessageVisibilityBatch",
      "sqs:ListQueueTags",
      "sqs:ListDeadLetterSourceQueues",
    ]
    resources = [
      "arn:aws:sqs:${var.region}:${data.aws_caller_identity.current.account_id}:*",
    ]
  }

  statement {
    sid       = "SqsListQueuesUnscoped"
    effect    = "Allow"
    actions   = ["sqs:ListQueues"]
    resources = ["*"]
  }

  # ECS exec, so the stub can be inspected in place when a simulated rail
  # misbehaves. Who may invoke execute-command is gated on the caller's role,
  # not this one.
  statement {
    sid    = "EcsExecSsmMessages"
    effect = "Allow"
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "task_inline" {
  name   = "${local.name_prefix}-malai-stub-ecs-task-inline"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_inline.json
}

#
# Service discovery — the stub's half of the Cloud Map namespace ecs-malai-api
# created.
#
# A records, NOT SRV, and the difference matters. malai-api's service uses SRV
# because its consumer is API Gateway, which reads AWS_INSTANCE_IPV4 and
# AWS_INSTANCE_PORT through the Cloud Map *API* and never performs a DNS
# lookup. The stub's consumer is an ordinary Java HTTP client resolving
# `malai-stub.<namespace>` by name — and an SRV-only service publishes no A
# record, so that lookup fails outright with UnknownHostException. Copying the
# SRV pattern across from malai-api produced exactly that: every sandbox and
# Confirmation-of-Payee call 500'd on an unresolvable host.
#
# The port is already in the URL (`:9090` in stub_base_url), so SRV's one
# advantage — carrying the port — buys nothing here.
#
# Note the matching consequence on the service below: with A records, ECS
# requires `service_registries` to omit container_name/container_port. Those
# are only valid for SRV.
#
resource "aws_service_discovery_service" "malai_stub" {
  name = "malai-stub"

  dns_config {
    namespace_id   = data.aws_service_discovery_dns_namespace.malai.id
    routing_policy = "MULTIVALUE"

    dns_records {
      type = "A"
      ttl  = 15
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }

  tags = local.tags
}

resource "aws_ecs_task_definition" "this" {
  family                   = "${local.name_prefix}-malai-stub"
  cpu                      = var.malai_stub_cpu
  memory                   = var.malai_stub_memory
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = "malai-stub"
      image     = local.image_uri
      essential = true
      portMappings = [
        {
          containerPort = var.malai_stub_container_port
          protocol      = "tcp"
        }
      ]
      environment = [
        # Required, not optional. spring-cloud-aws autoconfigures an
        # SqsAsyncClient at startup and resolves the region from the provider
        # chain; on Fargate there is no EC2 metadata service to fall back to, so
        # without this the client fails to build and the application exits
        # during bean creation. Verified by running the image locally without
        # it — the container dies rather than degrading.
        { name = "AWS_REGION", value = var.region },

        # Every gateway the stub calls back into lives in the malai-api task.
        # These default to localhost:8080 in application.yaml, which is correct
        # only when a developer runs both processes on one machine.
        { name = "MAL_FPS_GATEWAY_BASE_URL", value = local.malai_api_base_url },
        { name = "MAL_SWIFT_GATEWAY_BASE_URL", value = local.malai_api_base_url },
        { name = "MAL_BACS_GATEWAY_BASE_URL", value = local.malai_api_base_url },
        { name = "MAL_CARD_GATEWAY_BASE_URL", value = local.malai_api_base_url },

        # Ambient activity generators. Off by default, matching the stub's own
        # configuration: they inject payroll credits and card spend on a cron,
        # which is demo colour rather than correctness, and it would otherwise
        # start the moment this unit is applied.
        { name = "MAL_STUB_AMBIENT_PAYROLL_ENABLED", value = tostring(var.malai_stub_ambient_payroll_enabled) },
        { name = "MAL_STUB_AMBIENT_CARD_ENABLED", value = tostring(var.malai_stub_ambient_card_enabled) },
      ]
      healthCheck = {
        command     = ["CMD-SHELL", local.readiness_probe]
        interval    = 15
        timeout     = 5
        retries     = 3
        startPeriod = 90
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.task.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "malai-stub"
        }
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }

  tags = local.tags
}

resource "aws_ecs_service" "this" {
  name            = "${local.name_prefix}-malai-stub"
  cluster         = data.aws_ecs_cluster.malai_api.id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.malai_stub_desired_count

  enable_execute_command = true

  capacity_provider_strategy {
    capacity_provider = local.capacity_provider
    weight            = 1
    base              = 1
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = data.aws_subnets.private.ids
    security_groups  = [aws_security_group.task.id]
    assign_public_ip = false
  }

  # No container_name / container_port: those are only valid alongside SRV
  # records. With A records ECS registers the task IP alone, which is all a
  # hostname lookup needs.
  service_registries {
    registry_arn = aws_service_discovery_service.malai_stub.arn
  }

  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  tags = local.tags
}
