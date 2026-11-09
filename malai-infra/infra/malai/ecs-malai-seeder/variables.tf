variable "application" {
  description = "Application name (e.g. 'mal'). Combined with env for resource prefixes."
}

variable "env" {
  description = "Environment name (dev / prod). The seeder unit is gated on env == \"dev\" (Plan 02-15 D-2.15)."
}

variable "assume_role" {
  description = "Role to run infra-unit as. Shape matches the S3 backend `assume_role` block."
  type = object({
    role_arn = string
  })
}

variable "region" {
  description = "AWS region."
}

variable "malai_seeder_ecr_shared_account_id" {
  description = "AWS account ID that hosts the malai-seeder ECR repository."
  default     = "333333333333"
}

variable "malai_seeder_ecr_repository_name" {
  default = "malai-seeder"
}

variable "malai_seeder_initial_image_tag" {
  description = "Image tag used for the initial task definition. Subsequent runs via `aws ecs run-task --task-definition <family>:<rev>` can pin a different tag without re-applying terraform."
  default     = "latest"
}

variable "malai_seeder_log_retention_days" {
  default = 7
}

variable "bank_sort_code" {
  description = "UK sort code assigned to this bank, in dashed `XX-XX-XX` form. Mirrors the malai-api variable so seeded accounts use the same sort code as live writes."
  type        = string
}

variable "bank_bic" {
  description = "ISO 9362 BIC. Mirrors the malai-api variable."
  type        = string
}

variable "malai_stub_container_port" {
  description = "Port the stub listens on. Mirrors the variable of the same name on ecs-malai-stub, which is the source of truth."
  type        = number
  default     = 9090
}

variable "malai_stub_base_url" {
  description = <<-EOT
    In-VPC address of malai-stub, the simulated outside world. Owned by the
    ecs-malai-stub unit; this is the consuming half, and mirrors the identical
    variable on ecs-malai-api.

    The seeder needs it for one call: registering the seeded debit and credit
    card PANs with the stub scheme at the end of `seed`. That call is
    fail-open, so a wrong value costs no seed run — it just means ambient card
    spend never targets the seeded cards, which is a silent demo-fidelity gap
    rather than an error anyone notices.

    Derived from the shared Cloud Map namespace rather than hardcoded, so dev
    and prod each resolve their own.
  EOT
  type        = string
  default     = null
}

variable "deploy_seeder" {
  description = <<-EOT
    Whether to deploy the seeder's IAM roles, security group and one-shot task
    definition into this environment. Default true.

    This replaced a hard `var.env == "dev"` gate on 2026-08-10. Prod needs the
    seeder because its database is otherwise empty — a bank demo with no
    accounts in it is not a demo.

    Nothing here runs on a schedule or as a service; the task definition only
    executes when someone calls `aws ecs run-task`. Its default command is
    `reset`, which re-baselines the demo estate: a surgical clean of the
    seeded fixture identities (scoped by email) followed by migrate + seed.
    It leaves the audit chain, the system accounts and any non-seeded row —
    including an evaluator's self-registered account — untouched.

    Prefer malai-backend's `demo-reset.sh` over calling run-task directly;
    that is where the environment guard lives.

    Set false to keep an environment's seeder state empty.
  EOT
  type        = bool
  default     = true
}
