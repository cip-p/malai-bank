variable "application" {
  description = "Application name. This will be added as a prefix to all resources"
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

variable "app_mal" {
  description = "The name of the Malai application"
}

variable "app_malai_webapp" {
  description = "The name of the Malai webapp application"
}

variable "root_public_domain" {
  description = "Public domain"
}

variable "malai_webapp_domain" {
  description = "Malai webapp public domain"
}

variable "malai_webapp_cache_ttl_seconds" {
  description = "Malai webapp cloudfront cache TTL in seconds"
}

variable "enable_waf" {
  description = <<-EOT
    Whether to provision the WAFv2 WebACL and attach it to the webapp
    CloudFront distribution. Default true.

    Set false in dev "deep sleep" mode to drop ~$9/mo (one ACL + 4 rules).
    The distribution still serves the SPA bundle from S3 — without rate
    limiting / IP reputation / managed rule groups. Acceptable for a pet
    project at rest; not for prod.
  EOT
  type        = bool
  default     = true
}

variable "malai_webapp_alarms_enabled" {
  description = <<-EOT
    Wires the distribution's 5xx alarm and the TLS-expiry alarms to the shared
    `<application>-<env>-malai-api-alerts` SNS topic. That topic is created by
    ecs-malai-api's alarms, so this unit cannot plan while the backend stack is
    torn down — set false in any environment where ecs-malai-api is not
    deployed. Mirrors `malai_website_alarms_enabled` on the website unit.
  EOT
  type        = bool
}
