variable "application" {
  description = "Application name. This will be added as a prefix to all resources"
}

variable "app_mal" {
  description = "The name of the Malai application"
}

variable "app_malai_api" {
  description = "The name of the Malai API application"
}

variable "env" {
  description = "The environment name e.g. dev, prod. This will be added as a prefix to all resources"
}

variable "assume_role" {
  description = "Role to run infra-unit as. Shape matches the S3 backend `assume_role` block."
  type = object({
    role_arn = string
  })
}

variable "region" {
  description = "The AWS region"
}

variable "malai_api_ecr_shared_account_id" {
  description = "AWS account ID that hosts the malai-api ECR image (set by GitLab CI's release job)"
  default     = "333333333333"
}

variable "malai_api_ecr_repository_name" {
  default = "malai-api"
}

variable "malai_api_initial_image_tag" {
  description = "Image tag used only for the initial task definition. Later deployments update the tag out-of-band via ci.sh, and Terraform ignores changes to container_definitions."
  default     = "latest"
}

variable "malai_api_container_port" {
  default = 8080
}

variable "malai_api_instance_cpu" {
  description = "Fargate CPU reservation, in cpu units. Accepts AppRunner-style '1 vCPU' strings so config/malai/{env}/{env}.tfvars can be shared across runtimes."
  default     = "1 vCPU"
}

variable "malai_api_instance_memory" {
  description = "Fargate memory reservation. Accepts '2 GB' style strings."
  default     = "2 GB"
}

variable "malai_api_min_size" {
  default = 1
}

variable "malai_api_max_size" {
  default = 2
}

variable "malai_api_max_concurrency" {
  description = "Target request concurrency per task. Scales out when the CPU proxy crosses this."
  default     = 50
}

variable "malai_api_use_spot" {
  description = <<-EOT
    Run the malai-api task on FARGATE_SPOT instead of FARGATE. Default true.

    Spot is ~70% cheaper (1 vCPU / 2 GB ARM64: ~$29/mo on-demand vs ~$9/mo
    Spot). Malai's "prod" is a demo environment, not a bank, so the saving is
    worth more than the guarantee.

    What you are accepting: AWS can reclaim the task with a 2-minute warning
    when it needs the capacity back. At desired_count = 1 there is no second
    task to absorb that, so a reclaim means roughly 60-90 seconds of 503s
    while a replacement boots — and if Spot capacity is unavailable in the
    region at that moment, longer. Reclaims are uncommon but not rare, and
    they are not scheduled around your demo.

    Set false for any environment where an unattended visitor hitting a dead
    API is unacceptable. That is the only thing this flag protects against —
    it does not affect deploys, scaling, or the health-check path.
  EOT
  type        = bool
  default     = true
}

variable "malai_api_log_retention_days" {
  default = 30
}

variable "spring_profile" {
  description = "Spring Boot active profile (dev/prod)"
  default     = null
}

#
# Alerting (alarms.tf). Each threshold has a sane default for dev; override in
# prod.tfvars once we have real traffic baselines.
#
variable "alert_emails" {
  description = "Email addresses to subscribe to the malai-api SNS alerts topic. Each subscription needs manual confirmation via the link AWS sends. Use a distribution list for prod."
  type        = list(string)
  default     = []
}

variable "error_log_threshold" {
  description = "ERROR log lines per 5-minute window before paging. 0 = page on the first ERROR."
  type        = number
  default     = 5
}

# `alb_5xx_threshold` and `enable_request_alarms` moved to
# api-gateway-malai-api/variables.tf (as `api_5xx_threshold` and the same
# `enable_request_alarms`) when the ALB was replaced by Cloud Map — the 5xx
# alarm now keys on the HTTP API, which that unit owns. `enable_request_alarms`
# is still read from the shared env tfvars, just by a different unit.

variable "ecs_cpu_threshold" {
  description = "ECS service CPU utilisation percentage that, sustained over two 5-minute periods, pages."
  type        = number
  default     = 80
}

variable "ecs_memory_threshold" {
  description = "ECS service memory utilisation percentage that, sustained over two 5-minute periods, pages."
  type        = number
  default     = 80
}

variable "rds_cpu_threshold" {
  description = "RDS CPU utilisation percentage that, sustained over two 5-minute periods, pages."
  type        = number
  default     = 80
}

variable "rds_connections_threshold" {
  description = "DatabaseConnections value that, sustained over two 5-minute periods, pages. db.t3.medium tops out around 80; defaulting to 60 leaves headroom."
  type        = number
  default     = 60
}

#
# OpenTelemetry Java agent on the malai-api container.
#
# The agent JAR is baked into the image by `malai-api/Dockerfile` (added in the
# same commit that wired the ADOT collector sidecar). Older images don't carry
# it — attaching `-javaagent:/opt/otel/opentelemetry-javaagent.jar` to a JVM
# whose image is missing the file aborts startup and trips the deployment
# circuit breaker.
#
# Workflow:
#   1. Land the Dockerfile change. Release builds new image with agent baked in.
#   2. Once any post-Dockerfile image is in dev (deploy_full picked it up),
#      set `enable_otel_agent = true` in `config/malai/<env>/<env>.tfvars`,
#      lift the `ignore_changes = [container_definitions]` lifecycle in
#      `main.tf`, terraform apply, restore the lifecycle.
#   3. Force a deployment so the new task def with `JAVA_TOOL_OPTIONS` set
#      starts running. Traces appear in the X-Ray console.
#
# Until step 2 we run with the collector sidecar present but the agent
# dormant — sidecar idles, no traces, app boots cleanly on any image.
#
variable "enable_otel_agent" {
  description = "Attach the OpenTelemetry Java agent to the malai-api JVM. Requires the running image to contain /opt/otel/opentelemetry-javaagent.jar (post-Dockerfile change)."
  type        = bool
  default     = false
}

# Bank-level identifiers for AccountIdentifierFactory. These apply to every account created in
# this environment. Sort code in dashed form (`XX-XX-XX`). BIC is 8 or 11 chars; first 4 form
# the IBAN bank code. Configured per-env in `config/malai/<env>/<env>.tfvars`.
variable "bank_sort_code" {
  description = "UK sort code assigned to this bank, in dashed `XX-XX-XX` form."
  type        = string
}

variable "bank_bic" {
  description = "ISO 9362 BIC for this bank (8 or 11 chars). First 4 chars become the IBAN bank code."
  type        = string
}

variable "cors_allowed_origins" {
  description = "Origins permitted in Spring's `malai.cors` filter. Should match `cors_allowed_origins` on the api-gateway-malai-api module — single source of truth per env in `config/malai/<env>/<env>.tfvars`."
  type        = list(string)
}

# Payment screening posture. The backend defaults this to SHADOW on purpose: a real bank
# licensing the platform must never inherit enforcement it did not configure, so the
# shadow-to-enforce rollout is exactly one property. Malai's own environments are demos of
# the finished product, so they set ENFORCE here — the flip lives in infra rather than in
# the backend's application.properties precisely so the default stays honest for adopters.
#
# SHADOW  — every outbound FPS/SWIFT payment is screened and recorded as a Model Decision,
#           then proceeds regardless. The MDR fills; nothing is ever parked.
# ENFORCE — a flagged payment parks in REFERRED with its ledger Hold intact and moves only
#           when a human ruling reaches the MDR gate. This is the demo beat.
variable "screening_mode" {
  description = "Payment screening posture for this environment: SHADOW (record only) or ENFORCE (park flagged payments in REFERRED). Backend default is SHADOW; malai's demo environments set ENFORCE."
  type        = string
  default     = "SHADOW"

  validation {
    condition     = contains(["SHADOW", "ENFORCE"], var.screening_mode)
    error_message = "screening_mode must be SHADOW or ENFORCE."
  }
}

# Which Copilot Decider the malai-api JVM runs. `template` is the deterministic
# implementation the backend defaults to and the only one any test or e2e path
# touches; `anthropic` swaps in the real-provider adapter, which needs the key
# secret created in `copilot-provider.tf` and a budget large enough for a real
# model to answer inside. Nothing outside `anthropic` provisions the secret.
variable "copilot_decider" {
  description = "Copilot Decider implementation for this environment: template (deterministic, default) or anthropic (real provider)."
  type        = string
  default     = "template"

  validation {
    condition     = contains(["template", "anthropic"], var.copilot_decider)
    error_message = "copilot_decider must be template or anthropic."
  }
}

# The Copilot abandons an invocation that outlives this and records it TIMED_OUT.
# The backend's own default (10s) suits the deterministic decider; a real model
# answering over a full Timeline routinely needs more, so raise it in the same
# tfvars that select the provider.
variable "copilot_decider_timeout_ms" {
  description = "Milliseconds the Copilot gives its Decider before abandoning the invocation. Only applied when copilot_decider is anthropic."
  type        = number
  default     = 60000
}

variable "copilot_anthropic_model" {
  description = "Model id the Copilot's Anthropic adapter asks for. Keep the per-token prices in the backend's AnthropicDeciderProperties in step with whatever this names, or the recorded cost will be wrong."
  type        = string
  default     = "claude-opus-4-8"
}

variable "malai_stub_base_url" {
  description = <<-EOT
    In-VPC address of malai-stub, the simulated outside world. Owned by the
    ecs-malai-stub unit; this is the consuming half.

    Derived from the shared Cloud Map namespace rather than hardcoded, so dev
    and prod each resolve their own. Point it somewhere unreachable and the
    scheme rails and Confirmation of Payee fail — nothing else does, since the
    stub is not on any customer read path.
  EOT
  type        = string
  default     = null
}
