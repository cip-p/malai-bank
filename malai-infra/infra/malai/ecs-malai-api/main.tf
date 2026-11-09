locals {
  name_prefix = "${var.application}-${var.env}"

  # AppRunner-style strings from config/malai/{env}/{env}.tfvars — parse into Fargate values.
  # "1 vCPU" -> 1024 cpu units; "2 GB" -> 2048 MiB. Keeps the shared tfvars source of truth.
  cpu_vcpus = tonumber(regex("^([0-9.]+) vCPU$", var.malai_api_instance_cpu)[0])
  cpu_units = tostring(floor(local.cpu_vcpus * 1024))

  mem_gb = tonumber(regex("^([0-9.]+) GB$", var.malai_api_instance_memory)[0])
  mem_mb = tostring(floor(local.mem_gb * 1024))

  spring_profile = coalesce(var.spring_profile, var.env)

  capacity_provider = var.malai_api_use_spot ? "FARGATE_SPOT" : "FARGATE"

  # Defaults to the stub's Cloud Map name in this environment's namespace.
  malai_stub_base_url = coalesce(
    var.malai_stub_base_url,
    "http://malai-stub.${var.application}-${var.env}.malai.internal:9090",
  )

  image_uri = "${var.malai_api_ecr_shared_account_id}.dkr.ecr.${var.region}.amazonaws.com/${var.malai_api_ecr_repository_name}:${var.malai_api_initial_image_tag}"

  # Container health-check body — see the `healthCheck` block in the task
  # definition for why this is hand-rolled rather than a curl call.
  #
  # Terraform heredocs pass backslashes through verbatim (they do not process
  # escape sequences), so the `\r\n` below reach `printf` intact and printf
  # turns them into a real CRLF. Do not "fix" them to `\\r\\n` — that makes
  # printf emit literal backslashes and the request is never a valid HTTP
  # request line.
  readiness_probe = <<-BASH
    exec 3<>/dev/tcp/127.0.0.1/${var.malai_api_container_port}
    printf 'GET /ready HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
    read -r status_line <&3
    case "$status_line" in *' 200 '*) exit 0 ;; *) exit 1 ;; esac
  BASH

  # Single source of truth for which services need a Cognito service-token. Must match the
  # set in cognito-malai-api/service-jwt.tf (the secret naming convention is shared).
  services_requiring_service_jwt = toset([
    "malai-service",
  ])

  # Per-service Cognito-service env vars: `MAL_AWS_COGNITO_<SERVICE>_CLIENT_ID` etc, one
  # group per entry in `services_requiring_service_jwt`. The Spring side has one
  # `*ServiceTokenClient` subclass per caller that reads its own group.
  service_jwt_env_vars = flatten([
    for svc in local.services_requiring_service_jwt : [
      { name = "MAL_AWS_COGNITO_${upper(replace(svc, "-", "_"))}_CLIENT_ID", valueFrom = "${data.aws_secretsmanager_secret.cognito_service[svc].arn}:client_id::" },
      { name = "MAL_AWS_COGNITO_${upper(replace(svc, "-", "_"))}_CLIENT_SECRET", valueFrom = "${data.aws_secretsmanager_secret.cognito_service[svc].arn}:client_secret::" },
      { name = "MAL_AWS_COGNITO_${upper(replace(svc, "-", "_"))}_TOKEN_ENDPOINT_URL", valueFrom = "${data.aws_secretsmanager_secret.cognito_service[svc].arn}:token_endpoint_url::" },
    ]
  ])

  # Application secrets the task pulls at boot via ECS `secrets` injection.
  # Add new third-party integrations here — secret naming follows
  # `${name_prefix}-malai-api-${integration}` (see cognito-malai-api).
  app_secret_arns = concat(
    [
      data.aws_secretsmanager_secret.db_master.arn,
      data.aws_secretsmanager_secret.app_db.arn,
      data.aws_secretsmanager_secret.cognito.arn,
    ],
    [for s in data.aws_secretsmanager_secret.cognito_service : s.arn],
    aws_secretsmanager_secret.copilot_anthropic_api_key[*].arn,
  )

  tags = {
    Environment = var.env
    Project     = var.application
    Terraform   = true
    infra-unit  = "ecs-malai-api"
    Apps        = join(" ", [var.app_mal, var.app_malai_api])
  }
}

resource "aws_cloudwatch_log_group" "task" {
  name              = "/aws/ecs/${local.name_prefix}-malai-api"
  retention_in_days = var.malai_api_log_retention_days
  kms_key_id        = data.aws_kms_alias.logs.target_key_arn

  tags = local.tags
}

resource "aws_ecs_cluster" "this" {
  name = "${local.name_prefix}-malai-api"

  # Container Insights is disabled in dev to save ~$2-5/mo on enhanced
  # per-task / per-container metrics we don't actually look at. The free
  # service-level CPUUtilization / MemoryUtilization metrics in `AWS/ECS`
  # are *not* part of Container Insights — they're emitted regardless and
  # power our `ecs_cpu_high` / `ecs_memory_high` alarms.
  #
  # To re-enable for prod (richer per-container drilldowns), flip value to
  # `enabled`. No service redeploy needed; the cluster setting takes effect
  # for all subsequent metric publication.
  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  # ECS exec session logging — `DEFAULT` reuses the task container's `awslogs`
  # config, so anything typed in `aws ecs execute-command --interactive` lands
  # in the same `/aws/ecs/${name_prefix}-malai-api` log group as the app's
  # stdout (already encrypted with the logs CMK). Operator audit trail without
  # extra plumbing.
  configuration {
    execute_command_configuration {
      logging = "DEFAULT"
    }
  }

  tags = local.tags
}

#
# Both capacity providers are attached to the cluster; `var.malai_api_use_spot`
# picks which one the service actually runs on (see `capacity_provider_strategy`
# on the service). Attaching both means switching is a variable flip and an
# apply, with no cluster-level change.
#
resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = local.capacity_provider
    weight            = 1
    base              = 1
  }
}

#
# Security groups
#

# ECS task SG — only the API Gateway VPC Link ENIs can reach the container
# port. Egress open (needs RDS, ECR, etc).
#
# The ingress rule granting the VPC Link access lives in api-gateway-malai-api,
# which owns the VPC-link SG it sources from — same "consumer owns the grant"
# pattern as the DB SG ingress below. Before the ALB was removed that grant
# landed on a dedicated ALB SG; now it lands here, because the VPC Link talks
# to task IPs directly.
resource "aws_security_group" "task" {
  name        = "${local.name_prefix}-malai-api-task"
  description = "Fargate tasks for malai-api"
  vpc_id      = data.aws_vpc.primary.id

  tags = local.tags
}

resource "aws_security_group_rule" "task_egress" {
  type              = "egress"
  security_group_id = aws_security_group.task.id
  protocol          = "-1"
  from_port         = 0
  to_port           = 0
  cidr_blocks       = ["0.0.0.0/0"]
}

# Ingress rule lives on the DB SG but is owned here so the DB module stays runtime-agnostic.
resource "aws_security_group_rule" "db_ingress_from_task" {
  type                     = "ingress"
  security_group_id        = data.aws_security_group.db.id
  source_security_group_id = aws_security_group.task.id
  protocol                 = "tcp"
  from_port                = data.aws_db_instance.postgres.port
  to_port                  = data.aws_db_instance.postgres.port
  description              = "malai-api Fargate tasks"
}

#
# Service discovery (AWS Cloud Map) — the API Gateway HTTP API v2 VPC Link
# integrates with this directly, with no load balancer in the path.
#
# There used to be an internal ALB here. It cost ~$17/mo to route traffic to a
# single-task service that never used any of the layer-7 features justifying
# it (no host/path routing, no in-VPC TLS termination — API Gateway already
# does auth, throttling and access logs one hop upstream). HTTP API private
# integrations can target a Cloud Map service as well as an ALB or NLB, so the
# load balancer was pure cost. Cloud Map bills ~$0.10 per registered instance
# per month plus $1 per million discovery calls.
#
# The records are SRV, not A, and that is not cosmetic: SRV is what makes ECS
# register `AWS_INSTANCE_PORT` alongside `AWS_INSTANCE_IPV4`, and API Gateway
# needs both to build an upstream target. An A-record service would resolve to
# an IP with no port and the integration would fail.
#
# `health_check_custom_config` puts instance health under ECS's control: ECS
# marks an instance healthy or unhealthy from the container health check (see
# the `healthCheck` block in the task definition), and API Gateway only routes
# to healthy instances. That container health check is what replaces the ALB
# target-group health check as the readiness gate — without it, a task would
# start receiving traffic the moment it reached RUNNING, roughly 90 seconds
# before Spring Boot finishes booting.
#
resource "aws_service_discovery_private_dns_namespace" "this" {
  name        = "${local.name_prefix}.malai.internal"
  description = "Private service discovery for ${local.name_prefix}"
  vpc         = data.aws_vpc.primary.id

  tags = local.tags
}

resource "aws_service_discovery_service" "malai_api" {
  name = "malai-api"

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.this.id
    routing_policy = "MULTIVALUE"

    dns_records {
      type = "SRV"
      # Short TTL so a replaced task stops being resolved quickly. With one
      # task this mostly governs how fast a deploy's old IP stops being
      # handed out.
      ttl = 15
    }

    # A records as well as SRV, because this service has two consumers that
    # discover it differently. API Gateway reads AWS_INSTANCE_IPV4 and
    # AWS_INSTANCE_PORT through the Cloud Map API, which needs SRV. malai-stub
    # calls back into the FPS / SWIFT / BACS / card gateways over HTTP,
    # resolving this name with an ordinary A lookup — and an SRV-only service
    # publishes no A record, so those callbacks died with
    # `UnknownHostException: ... Name has no usable address`, leaving every
    # outbound payment with its funds held and no settlement.
    #
    # Both types on one service rather than two services: ECS permits only one
    # `service_registries` entry per service, so a second Cloud Map service
    # cannot be registered into.
    dns_records {
      type = "A"
      ttl  = 15
    }

  }

  # No `health_check_custom_config`, deliberately, and it is worth knowing why
  # before adding one back.
  #
  # The block was written here originally with `failure_threshold = 1`, then
  # emptied to `{}` when the provider deprecated that argument. An empty block
  # is treated as absent, so the service was created without custom health
  # config — and because the block is immutable, declaring it now plans as
  # "forces replacement", which fails with `ResourceInUse: Service contains
  # registered instances` for as long as a task is running. Declaring it made
  # this unit un-appliable.
  #
  # What that costs: Cloud Map reports instance health as UNKNOWN rather than
  # ECS driving it from the container health check. Nothing depends on it —
  # Cloud Map does not exclude UNKNOWN instances from discovery, API Gateway
  # routes to them, and readiness is really enforced by the container health
  # check plus the deployment circuit breaker, which gate whether a task ever
  # reaches a routable state.
  #
  # To adopt it properly: scale the service to 0 so no instances are
  # registered, apply (the service is replaced), then scale back. That is a
  # deliberate outage, not something to slip into an unrelated apply.

  tags = local.tags
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
  name               = "${local.name_prefix}-malai-api-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# The image lives in the shared account's ECR. Execution role needs pull permission there
# in addition to the managed policy's own-account coverage.
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
    sid       = "ReadAppSecrets"
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = local.app_secret_arns
  }

  # ECS needs to decrypt the Secrets Manager values it injects into the task as env vars.
  # The secrets CMK protects those values (the db CMK protects RDS storage, which ECS
  # never touches directly — Spring talks to RDS over TLS, not KMS).
  statement {
    sid    = "DecryptAppSecrets"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
    ]
    resources = [data.aws_kms_alias.secrets.target_key_arn]
  }
}

resource "aws_iam_role_policy" "execution_inline" {
  name   = "${local.name_prefix}-malai-api-ecs-execution-inline"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_inline.json
}

resource "aws_iam_role" "task" {
  name               = "${local.name_prefix}-malai-api-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.task_assume.json

  tags = local.tags
}

# Baseline application permissions — Cognito user pool operations for the authenticated flows.
# Widen this policy when the app starts calling other AWS services (SES, S3, etc).
data "aws_iam_policy_document" "task_inline" {
  statement {
    sid    = "CognitoUserPool"
    effect = "Allow"
    actions = [
      "cognito-idp:AdminInitiateAuth",
      "cognito-idp:AdminCreateUser",
      "cognito-idp:AdminSetUserPassword",
      "cognito-idp:AdminGetUser",
      "cognito-idp:AdminUpdateUserAttributes",
      "cognito-idp:AdminDeleteUser",
      "cognito-idp:AdminConfirmSignUp",
      # RegistrationOrchestrator binds new users to the "customer" group so their
      # JWTs carry cognito:groups → ROLE_CUSTOMER for @PreAuthorize gates.
      "cognito-idp:AdminAddUserToGroup",
      "cognito-idp:SignUp",
      "cognito-idp:ConfirmSignUp",
      "cognito-idp:InitiateAuth",
      "cognito-idp:RespondToAuthChallenge",
      "cognito-idp:ForgotPassword",
      "cognito-idp:ConfirmForgotPassword",
      "cognito-idp:ResendConfirmationCode",
      "cognito-idp:GetUser",
      "cognito-idp:DescribeUserPool",
      "cognito-idp:DescribeUserPoolClient",
    ]
    resources = [
      "arn:aws:cognito-idp:${var.region}:${data.aws_caller_identity.current.account_id}:userpool/*",
    ]
  }

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

  # ECS exec — task role needs to open an SSM control/data channel back to the
  # caller. Operator perms (who can invoke `aws ecs execute-command`) are gated
  # on the CI deploy role / admin roles, which the task itself doesn't carry.
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

  # ADOT collector sidecar (see container_definitions in the task) forwards
  # OpenTelemetry spans to AWS X-Ray. None of these APIs support resource-level
  # scoping, hence "*". CloudWatch metric Put is here because the same collector
  # also exports Spring Boot Micrometer metrics to CloudWatch via EMF.
  statement {
    sid    = "AdotXrayCloudWatch"
    effect = "Allow"
    actions = [
      "xray:PutTraceSegments",
      "xray:PutTelemetryRecords",
      "xray:GetSamplingRules",
      "xray:GetSamplingTargets",
      "xray:GetSamplingStatisticSummaries",
      "logs:PutLogEvents",
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:DescribeLogStreams",
      "logs:DescribeLogGroups",
      "cloudwatch:PutMetricData",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "task_inline" {
  name   = "${local.name_prefix}-malai-api-ecs-task-inline"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_inline.json
}

#
# Task definition + service
#
# container_definitions is written by Terraform for the initial deploy, then
# ci.sh updates the image tag via `aws ecs update-service --force-new-deployment`
# combined with a new task-definition revision (see malai-backend/ci.sh).
# We ignore changes here so running `terraform apply` doesn't roll the service
# back to :latest mid-deploy.
resource "aws_ecs_task_definition" "this" {
  family                   = "${local.name_prefix}-malai-api"
  cpu                      = local.cpu_units
  memory                   = local.mem_mb
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  # Run on Fargate Graviton2 (ARM64) to save ~20% on Fargate cost vs x86_64.
  # The malai-api image is already built multi-arch by `ci.sh release` (the
  # buildx invocation passes `--platform linux/amd64,linux/arm64`), so the
  # ECR manifest list lets Fargate pick the arm64 variant automatically.
  # Both sidecar images we use (`aws-otel-collector`, the Spring Boot JVM
  # image) ship arm64 manifests as well.
  #
  # To revert for prod or for any image that's amd64-only, drop this block —
  # Fargate defaults to `LINUX` / `X86_64`.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  #
  # Two containers in this task:
  #
  #   1. `malai-api` — the Spring Boot app. The OTel Java agent (baked into the
  #      image at /opt/otel/opentelemetry-javaagent.jar) is enabled via
  #      JAVA_TOOL_OPTIONS and exports OTLP/gRPC to localhost:4317, where the
  #      ADOT collector listens.
  #
  #   2. `aws-otel-collector` — AWS-distributed OpenTelemetry collector running
  #      as a sidecar with the canned `ecs/container-insights/otel-config`
  #      config. It receives OTLP from the app, forwards traces to X-Ray, and
  #      exports container/EMF metrics to CloudWatch. Marked non-essential so
  #      a collector crash doesn't kill the whole task; if you ever flip this
  #      to essential, ECS replacements will cascade on every collector OOM.
  #
  # Note: container_definitions has `ignore_changes` below — terraform writes
  # the initial revision; ci.sh updates the image tag. New env vars (e.g. for
  # the collector wiring) only take effect on a fresh terraform apply, which
  # creates a new task definition revision and forces a service redeploy.
  #
  container_definitions = jsonencode([
    {
      name      = "malai-api"
      image     = local.image_uri
      essential = true
      portMappings = [
        {
          containerPort = var.malai_api_container_port
          protocol      = "tcp"
        }
      ]
      # Readiness gate. This is what ECS reports to Cloud Map, and therefore
      # what decides whether API Gateway will route to this task — it replaces
      # the ALB target-group health check that used to poll /ready.
      #
      # It is a hand-rolled HTTP request because the runtime image has no HTTP
      # client: eclipse-temurin:25-jre ships neither curl nor wget (nor nc, nor
      # python3). Bash is present, so `/dev/tcp` opens the socket and `printf`
      # writes the request. The alternative is a one-line `apt-get install
      # curl` in malai-backend's Dockerfile, which is more conventional but
      # crosses a repo boundary and needs an image rebuild to take effect.
      #
      # `CMD` rather than `CMD-SHELL` on purpose: CMD-SHELL runs `/bin/sh -c`,
      # and on Ubuntu /bin/sh is dash, which has no `/dev/tcp`.
      #
      # Only a 200 passes. A connection refused (JVM not listening), an
      # unresolvable host, or any non-200 from /ready all exit non-zero.
      healthCheck = {
        command = ["CMD", "bash", "-c", local.readiness_probe]
        # 3 failed checks 15s apart => unhealthy ~45s after the app stops
        # answering.
        interval = 15
        timeout  = 5
        retries  = 3
        # Covers Spring Boot's cold start. Failures before this elapses don't
        # count toward `retries`, so a slow boot delays readiness rather than
        # killing the task.
        startPeriod = 120
      }
      # `concat` so we can append the JAVA_TOOL_OPTIONS entry only when the
      # OTel agent is enabled (avoids attaching `-javaagent:` to an image that
      # doesn't ship the JAR — would crash the JVM on boot).
      environment = concat([
        { name = "SPRING_PROFILES_ACTIVE", value = local.spring_profile },
        # Phase 2 D-2.12: DATABASE_URL is the single source of truth for the
        # JDBC URL — referenced by both `spring.datasource.url` (runtime,
        # malai_app role) and `spring.flyway.url` (migrations, master role) in
        # application.properties. RDS enforces TLS via `rds.force_ssl=1`.
        { name = "DATABASE_URL", value = "jdbc:postgresql://${data.aws_db_instance.postgres.endpoint}/${data.aws_db_instance.postgres.db_name}?sslmode=require" },
        # auth-service, account-service and account-holder-service are bundled into
        # the malai-api JVM, so internal client calls loop back to localhost.
        { name = "MAL_ACCOUNT_HOLDER_BASE_URL", value = "http://localhost:${var.malai_api_container_port}" },
        # The simulated outside world — other banks' Confirmation-of-Payee
        # directory and the FPS / SWIFT / BACS / card scheme endpoints. Unlike
        # the line above this is NOT a loopback: malai-stub is its own task,
        # reached over Cloud Map in the same namespace. The FPS / SWIFT / BACS
        # scheme base URLs all default to this value in application.properties,
        # so this single variable points every rail at the stub.
        #
        # Without it the application falls back to http://localhost:9090, where
        # nothing listens — which is what produced a connection-refused error
        # every ~10s from the payment-screening and AML sweep schedulers.
        { name = "MAL_STUB_BASE_URL", value = local.malai_stub_base_url },
        # Bank-level identifiers used by AccountIdentifierFactory to build SORT_CODE + IBAN_BIC
        # records on every account creation. Sort code in dashed form; first 4 chars of the
        # BIC become the IBAN bank code.
        { name = "MAL_BANK_SORT_CODE", value = var.bank_sort_code },
        { name = "MAL_BANK_BIC", value = var.bank_bic },

        { name = "MAL_CORS_ALLOWED_ORIGINS", value = join(",", var.cors_allowed_origins) },

        # Payment screening posture — see `var.screening_mode`. The backend defaults to
        # SHADOW so an adopting bank never inherits enforcement; malai's own environments
        # opt in to ENFORCE here so a flagged payment actually parks in REFERRED and the
        # MDR ruling loop is demonstrable end-to-end.
        { name = "MAL_PAYMENTS_SCREENING_MODE", value = var.screening_mode },

        # Cognito user-pool ID for Admin* operations (CognitoAdminClient).
        # Same pool the SECRET_HASH flows hit; the client_id/secret/issuer for
        # those flows still live in the cognito secret above.
        { name = "MAL_AWS_COGNITO_USER_POOL_ID", value = tolist(data.aws_cognito_user_pools.malai.ids)[0] },

        # ---- OpenTelemetry / X-Ray wiring ----
        # OTEL_SERVICE_NAME and the rest are harmless if the agent isn't loaded;
        # the agent itself is gated on `var.enable_otel_agent` (see below).
        { name = "OTEL_SERVICE_NAME", value = "malai-api" },
        { name = "OTEL_RESOURCE_ATTRIBUTES", value = "deployment.environment=${var.env}" },
        # OTLP/gRPC to the sidecar over loopback (awsvpc shared network namespace).
        { name = "OTEL_EXPORTER_OTLP_ENDPOINT", value = "http://localhost:4317" },
        { name = "OTEL_EXPORTER_OTLP_PROTOCOL", value = "grpc" },
        # X-Ray uses 1-byte epoch + 11-byte random ID format. The agent has a
        # built-in propagator for it; enable both so we still understand W3C
        # traceparent if a future caller sets one.
        { name = "OTEL_PROPAGATORS", value = "xray,tracecontext,baggage" },
        { name = "OTEL_TRACES_SAMPLER", value = "parentbased_traceidratio" },
        # Sample every request — dev throughput is low enough that 100% is fine
        # and we want every API call visible in X-Ray. Drop towards prod-grade
        # (e.g. 0.1) once load grows or X-Ray ingestion cost becomes a concern.
        { name = "OTEL_TRACES_SAMPLER_ARG", value = "1.0" },
        # We export traces to the collector but skip metrics/logs over OTLP —
        # metrics already go via Micrometer + CloudWatch namespace, logs already
        # go via awslogs. Avoids double-billing.
        { name = "OTEL_METRICS_EXPORTER", value = "none" },
        { name = "OTEL_LOGS_EXPORTER", value = "none" },
        ],
        var.enable_otel_agent ? [
          # Attach the agent on JVM start.
          { name = "JAVA_TOOL_OPTIONS", value = "-javaagent:/opt/otel/opentelemetry-javaagent.jar" },
        ] : [],
        # Copilot Decider selection. Emitted only when the real provider is
        # selected, so an environment on the default deterministic decider gets
        # the same task definition it has today and the backend's own defaults.
        local.copilot_provider_enabled ? [
          { name = "MAL_COPILOT_DECIDER", value = var.copilot_decider },
          { name = "MAL_COPILOT_DECIDER_TIMEOUT_MS", value = tostring(var.copilot_decider_timeout_ms) },
          { name = "MAL_COPILOT_ANTHROPIC_MODEL", value = var.copilot_anthropic_model },
        ] : []
      )
      secrets = concat(
        [
          # Phase 2 D-2.12: two-datasource pattern — runtime DataSource uses
          # malai_app (append-only on audit_events); Flyway uses the master
          # role to run V12+ which CREATE ROLE / GRANT.
          { name = "MAL_APP_DB_USERNAME", valueFrom = "${data.aws_secretsmanager_secret.app_db.arn}:username::" },
          { name = "MAL_APP_DB_PASSWORD", valueFrom = "${data.aws_secretsmanager_secret.app_db.arn}:password::" },
          { name = "MAL_DB_MASTER_USERNAME", valueFrom = "${data.aws_secretsmanager_secret.db_master.arn}:username::" },
          { name = "MAL_DB_MASTER_PASSWORD", valueFrom = "${data.aws_secretsmanager_secret.db_master.arn}:password::" },
          { name = "MAL_AWS_COGNITO_CLIENT_ID", valueFrom = "${data.aws_secretsmanager_secret.cognito.arn}:client_id::" },
          { name = "MAL_AWS_COGNITO_CLIENT_SECRET", valueFrom = "${data.aws_secretsmanager_secret.cognito.arn}:client_secret::" },
          { name = "MAL_AWS_COGNITO_JWT_TOKEN_ISSUER", valueFrom = "${data.aws_secretsmanager_secret.cognito.arn}:issuer::" },
        ],
        # Cognito service (client_credentials) — one set of three env vars per calling service
        # in `local.services_requiring_service_jwt`. Each service's own *ServiceTokenClient
        # reads its own `MAL_AWS_COGNITO_<SERVICE>_*` group to mint JWTs for cross-service calls.
        local.service_jwt_env_vars,
        # The Copilot's provider key — see copilot-provider.tf. Present only when
        # the real provider is selected; the deterministic decider needs no key.
        local.copilot_provider_enabled ? [
          { name = "MAL_COPILOT_ANTHROPIC_API_KEY", valueFrom = "${aws_secretsmanager_secret.copilot_anthropic_api_key[0].arn}:api_key::" },
        ] : [],
      )
      dependsOn = [
        # Don't start the JVM until the collector's OTLP listener is up — otherwise
        # the agent's startup spans get dropped and re-attempted in a noisy backoff.
        { containerName = "aws-otel-collector", condition = "START" },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.task.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "malai-api"
        }
      }
    },
    {
      name = "aws-otel-collector"
      # Pinned to a specific upstream release so we never silently roll onto a
      # broken collector. Bump by checking
      # https://github.com/aws-observability/aws-otel-collector/releases (or
      # `gh release list -R aws-observability/aws-otel-collector`); the GitHub
      # tag matches the ECR tag 1:1. Updates require the lifecycle dance on
      # this resource — see the comment by `lifecycle { ignore_changes }`.
      image     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.47.0"
      essential = false
      # Canned config bundled in the collector image. Suitable for ECS Fargate
      # with X-Ray + CloudWatch EMF; for custom pipelines build a config file
      # into a custom image and pass `--config /etc/...`.
      command = ["--config=/etc/ecs/ecs-default-config.yaml"]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.task.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "aws-otel-collector"
        }
      }
    }
  ])

  # ci.sh's `run_deploy` reads the live task def, swaps containerDefinitions[0].image,
  # and registers a new revision. It leaves the other containers (e.g. the
  # aws-otel-collector sidecar) untouched, so once terraform writes them once,
  # every subsequent deploy carries them forward.
  #
  # To push a structural change (new sidecar, new env var on the app, …):
  #   1. Comment out the `ignore_changes` line below.
  #   2. terraform apply.
  #   3. Restore the line.
  # `terraform apply` then produces a new revision; ci.sh deploys preserve it.
  lifecycle {
    ignore_changes = [container_definitions]
  }

  tags = local.tags
}

resource "aws_ecs_service" "this" {
  name            = "${local.name_prefix}-malai-api"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.this.arn
  desired_count   = var.malai_api_min_size

  # Replaces `launch_type = "FARGATE"`. The two are mutually exclusive, and a
  # service that sets `launch_type` ignores the cluster's default strategy
  # entirely — so the strategy has to be declared here for Spot to take effect.
  #
  # There is no on-demand fallback entry, and that is not an oversight. A
  # fallback needs either `base = 1` on FARGATE (which pins the only task to
  # on-demand and saves nothing at desired_count = 1) or a non-zero weight on
  # FARGATE (which splits traffic across providers — meaningless with one
  # task). At this scale it is genuinely either/or; see `malai_api_use_spot`
  # for what that costs you.
  capacity_provider_strategy {
    capacity_provider = local.capacity_provider
    weight            = 1
    base              = 1
  }

  # `aws ecs execute-command --interactive --command sh` works once the task
  # restarts. Requires the task role's SSM Messages perms (above) plus the
  # cluster's `execute_command_configuration` for log destination.
  enable_execute_command = true

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # No `health_check_grace_period_seconds`. Spring Boot still needs ~60-90s
  # before it answers /ready, but that allowance now lives in the container
  # health check's `startPeriod` (see the task definition) rather than at the
  # service level — failures inside the start period don't count toward the
  # retry budget, so a slow-but-fine boot can't trip the circuit breaker.
  # Setting both would be redundant, and the service-level parameter is
  # documented against load-balanced services.

  # ECS marks a deployment FAILED if too many task replacements fail in a row,
  # then rolls back to the previous task definition. ci.sh asserts the PRIMARY
  # deployment matches the just-registered ARN to surface the rollback as a
  # non-zero exit.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = data.aws_subnets.private.ids
    security_groups  = [aws_security_group.task.id]
    assign_public_ip = false
  }

  # Registers each task's IP + port into the Cloud Map service the API Gateway
  # VPC Link integrates with. `container_name` + `container_port` are required
  # for SRV records — they're what ECS puts in `AWS_INSTANCE_PORT`.
  service_registries {
    registry_arn   = aws_service_discovery_service.malai_api.arn
    container_name = "malai-api"
    container_port = var.malai_api_container_port
  }


  # ci.sh updates the task definition out-of-band; without this, `terraform apply`
  # would fight with deployments.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  tags = local.tags
}

#
# Auto-scaling
#

resource "aws_appautoscaling_target" "this" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.this.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.malai_api_min_size
  max_capacity       = var.malai_api_max_size
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${local.name_prefix}-malai-api-cpu"
  service_namespace  = aws_appautoscaling_target.this.service_namespace
  resource_id        = aws_appautoscaling_target.this.resource_id
  scalable_dimension = aws_appautoscaling_target.this.scalable_dimension
  policy_type        = "TargetTrackingScaling"

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 60
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
  }
}

#
# No scheduled scaling — dev is "off by default", woken up manually via
# `bin/wake.sh` and put back to sleep via `bin/sleep.sh`. Combined with
# `malai_api_min_size = 0` in dev.tfvars, the autoscaling target permits
# manual scale-to-0 (otherwise autoscaling would always pull desired_count
# back up to the floor).
#
# For prod: set `malai_api_min_size` to your real floor (≥1) and don't
# wake/sleep manually.
#

#
# Rotation redeploy hook
#
# When Secrets Manager rotates `mal-${env}-db-master`, ECS tasks already running keep
# using the OLD password (it was injected as an env var at task start). They'd fail
# next time JDBC reconnects. EventBridge catches the rotation event and a tiny lambda
# triggers `update-service --force-new-deployment`, which rolls tasks one by one to
# pick up the new password.
#
data "archive_file" "rotation_redeploy" {
  type        = "zip"
  output_path = "${path.module}/.terraform/rotation-redeploy.zip"

  source {
    filename = "index.py"
    content  = <<-PY
      import os
      import boto3

      ecs = boto3.client("ecs")

      def handler(event, context):
          print(f"Triggering force-new-deployment on cluster=$${os.environ['CLUSTER_NAME']} service=$${os.environ['SERVICE_NAME']} due to event: $${event}")
          ecs.update_service(
              cluster=os.environ["CLUSTER_NAME"],
              service=os.environ["SERVICE_NAME"],
              forceNewDeployment=True,
          )
    PY
  }
}

resource "aws_iam_role" "rotation_redeploy" {
  name = "${local.name_prefix}-malai-api-rotation-redeploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "rotation_redeploy_basic" {
  role       = aws_iam_role.rotation_redeploy.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "rotation_redeploy" {
  statement {
    sid    = "ForceNewDeployment"
    effect = "Allow"
    actions = [
      "ecs:UpdateService",
      "ecs:DescribeServices",
    ]
    resources = [aws_ecs_service.this.id]
  }
}

resource "aws_iam_role_policy" "rotation_redeploy" {
  name   = "${local.name_prefix}-malai-api-rotation-redeploy"
  role   = aws_iam_role.rotation_redeploy.id
  policy = data.aws_iam_policy_document.rotation_redeploy.json
}

resource "aws_lambda_function" "rotation_redeploy" {
  function_name = "${local.name_prefix}-malai-api-rotation-redeploy"
  filename      = data.archive_file.rotation_redeploy.output_path
  runtime       = "python3.13"
  handler       = "index.handler"
  role          = aws_iam_role.rotation_redeploy.arn
  timeout       = 30

  source_code_hash = data.archive_file.rotation_redeploy.output_base64sha256

  environment {
    variables = {
      CLUSTER_NAME = aws_ecs_cluster.this.name
      SERVICE_NAME = aws_ecs_service.this.name
    }
  }

  tags = local.tags
}

resource "aws_cloudwatch_log_group" "rotation_redeploy" {
  name              = "/aws/lambda/${aws_lambda_function.rotation_redeploy.function_name}"
  retention_in_days = var.malai_api_log_retention_days
  kms_key_id        = data.aws_kms_alias.logs.target_key_arn

  tags = local.tags
}

# EventBridge rule that fires on a successful rotation of the db_master secret.
# Source `aws.secretsmanager`, eventName `RotationSucceeded`, scoped to our specific
# secret ARN so we don't react to rotations of other secrets in the account.
resource "aws_cloudwatch_event_rule" "rotation_succeeded" {
  name        = "${local.name_prefix}-malai-api-rotation-succeeded"
  description = "Triggers ECS force-new-deployment after db_master rotation"

  event_pattern = jsonencode({
    source      = ["aws.secretsmanager"]
    detail-type = ["AWS Service Event via CloudTrail"]
    detail = {
      eventName = ["RotationSucceeded"]
      requestParameters = {
        secretId = [data.aws_secretsmanager_secret.db_master.arn]
      }
    }
  })

  tags = local.tags
}

resource "aws_cloudwatch_event_target" "rotation_redeploy" {
  rule      = aws_cloudwatch_event_rule.rotation_succeeded.name
  target_id = "rotation-redeploy"
  arn       = aws_lambda_function.rotation_redeploy.arn
}

resource "aws_lambda_permission" "rotation_redeploy_invoke" {
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.rotation_redeploy.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.rotation_succeeded.arn
}

