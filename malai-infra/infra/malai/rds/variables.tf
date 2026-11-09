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

variable "rds_engine_version" {
  description = <<-EOT
    Postgres engine version. Deliberately the major version only, not a pinned
    minor.

    This was `17.2` until 2026-08-10, when the first prod apply failed with
    `InvalidParameterCombination: Cannot find version 17.2 for postgres` — AWS
    had retired that minor and the pin outlived it. Specifying the major
    version alone lets RDS select the current default minor (17.5-17.10 as of
    that date), so the same failure can't recur every time AWS ages a minor
    out. `auto_minor_version_upgrade` then keeps it patched in place.

    The `postgres17` parameter-group family covers every 17.x, so nothing
    downstream needs to change as the minor moves. Pin a specific minor here
    only if a version-sensitive bug forces it.
  EOT
  default     = "17"
}

variable "rds_instance_class" {
  default = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  default = 20
}

variable "rds_max_allocated_storage" {
  default = 100
}

variable "rds_storage_type" {
  default = "gp3"
}

variable "rds_backup_retention_days" {
  default = 7
}

variable "rds_backup_window" {
  default = "03:00-04:00"
}

variable "rds_maintenance_window" {
  default = "Mon:04:00-Mon:05:00"
}

variable "rds_multi_az" {
  description = "Run the DB across 2 AZs. Set to false for cheaper single-AZ setups; the subnet group still spans 3 AZs so we can flip this to true without reshaping the network."
  type        = bool
  default     = false
}

variable "rds_skip_final_snapshot" {
  default = false
}

variable "rds_apply_immediately" {
  default = false
}

variable "rds_database_name" {
  default = "malai"
}

variable "rds_master_username" {
  default = "malai"
}

variable "rds_port" {
  default = 5432
}
