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

variable "app_malai_website" {
  description = "The name of the Malai website application"
}

variable "root_public_domain" {
  description = "Public domain"
}

variable "malai_website_domain" {
  description = "Malai website public domain"
}

variable "malai_website_cache_ttl_seconds" {
  description = "Malai website cloudfront cache TTL in seconds"
}

variable "malai_website_alarms_enabled" {
  description = <<-EOT
    Wires the distribution's 5xx alarm and the TLS-expiry alarms to the shared
    `<application>-<env>-malai-api-alerts` SNS topic. That topic is created by
    ecs-malai-api's alarms, which are only deployed in prod — dev has no
    alerting stack at all, so dev sets this false to keep the unit applyable
    there.
  EOT
  type        = bool
}
