variable "application" {
  description = "Application name. This will be added as a prefix to all resources"
}

variable "app_mal" {
  description = "The name of the Malai application"
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

variable "malai_stub_ecr_shared_account_id" {
  description = "Account holding the shared ECR registry the stub image is pulled from."
  default     = "333333333333"
}

variable "malai_stub_ecr_repository_name" {
  default = "malai-stub"
}

variable "malai_stub_initial_image_tag" {
  description = <<-EOT
    Image tag terraform writes into the first task-definition revision.
    `ci.sh release_stub` + a manual task-definition update own the tag after
    that, and `ignore_changes = [container_definitions]` keeps terraform from
    rolling it back.
  EOT
  default     = "latest"
}

variable "malai_stub_consumers" {
  description = <<-EOT
    Unqualified names of the units whose Fargate tasks call malai-stub. Each
    resolves to the security group `<application>-<env>-<name>-task`.

    This list is the guard. Reaching the stub takes two rules in opposite
    directions on two different security groups, and omitting the inbound half
    fails as a connection that hangs for two minutes before timing out rather
    than as anything that looks like a network fault. malai-api hit that, and
    malai-seeder hit it again afterwards — its card-PAN registration is
    fail-open, so the seed reported success on every run while ambient card
    spend silently never targeted the seeded cards. Generating both rules from
    one list means a consumer cannot be declared and left half-wired.

    A name whose security group does not exist in this environment is skipped,
    so `deploy_seeder = false` needs no special case here.

    Adding a consumer: put it in this list, and set MAL_STUB_BASE_URL on its
    task definition. Those are the only two steps.
  EOT
  type        = list(string)
  default     = ["malai-api", "malai-seeder"]
}

variable "malai_stub_container_port" {
  description = "Port the stub listens on. Matches `server.port` in malai-stub's application.yaml."
  type        = number
  default     = 9090
}

variable "malai_stub_cpu" {
  description = <<-EOT
    Fargate CPU units. 256 (0.25 vCPU) because the stub answers canned
    responses and runs no database — it is the smallest Fargate size that
    exists, and there is nothing here to justify more.
  EOT
  default     = "256"
}

variable "malai_stub_memory" {
  description = "Fargate memory in MiB. 512 is the minimum permitted alongside 256 CPU units."
  default     = "512"
}

variable "malai_stub_desired_count" {
  description = <<-EOT
    Tasks to run. 1 in every environment; set 0 to park the stub without
    destroying the unit (dev sleep, or if the simulated rails are not being
    demonstrated).
  EOT
  type        = number
  default     = 1
}

variable "malai_stub_use_spot" {
  description = <<-EOT
    Run on FARGATE_SPOT. Default true, and the case is stronger here than for
    malai-api: the stub holds no state, and a reclaim degrades Confirmation of
    Payee and the simulated rails for ~60-90s rather than taking the bank down.

    ~$2/mo on Spot at 0.25 vCPU / 0.5 GB, against ~$7 on-demand.
  EOT
  type        = bool
  default     = true
}

variable "malai_stub_log_retention_days" {
  default = 30
}

variable "malai_api_internal_base_url" {
  description = <<-EOT
    Override for where the stub calls back into malai-api's FPS / SWIFT / BACS
    / card gateways. Leave null: the default is derived from the environment's
    Cloud Map namespace, so dev and prod each resolve their own.

    Deliberately the in-VPC address rather than the public API Gateway domain —
    these are internal callbacks that should never leave the VPC or traverse
    the JWT authorizer.
  EOT
  type        = string
  default     = null
}

variable "malai_api_container_port" {
  description = "Port malai-api listens on, for the stub's callback URL. Must match ecs-malai-api."
  type        = number
  default     = 8080
}

variable "malai_stub_ambient_payroll_enabled" {
  description = <<-EOT
    Whether the stub injects recurring payroll credits on a cron.

    Off by default, matching the stub's own configuration. It generates demo
    colour — the Exec deposits metric moving on its own — rather than anything
    correctness depends on, and enabling it means the bank's numbers change
    while nobody is using it.
  EOT
  type        = bool
  default     = false
}

variable "malai_stub_ambient_card_enabled" {
  description = "Whether the stub injects recurring card spend on a cron. Off by default, same reasoning as the payroll generator."
  type        = bool
  default     = false
}
