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

variable "app_malai_api" {
  description = "The name of the Malai API application"
}

variable "api_gw_log_retention" {
  description = "Cloudwatch log groups log retention in days"
  default     = 14
}

variable "root_public_domain" {
  description = "Public domain"
}

variable "malai_api_domain" {
  description = "Malai API public domain"
}

variable "cors_allowed_origins" {
  description = "Origins allowed for CORS at the API Gateway level. Set per-env in `config/malai/<env>/<env>.tfvars`. Typically the webapp domain serving traffic in that env; dev additionally permits `http://localhost:5173` so a developer can point Vite at the deployed dev backend. Prod-like envs must NOT include localhost. No wildcards. Required, no default — every env must declare its origins explicitly so a freshly-cloned env can't ship as wide-open `[\"*\"]` by accident."
  type        = list(string)
}

variable "enable_waf" {
  description = <<-EOT
    Whether to provision the WAFv2 WebACL and attach it to the api-gateway
    CloudFront distribution. Default true.

    Set false in dev "deep sleep" mode to drop ~$9/mo (one ACL + 4 rules).
    The CloudFront distribution still exists while disabled, but with ECS
    already at desired_count=0 there is nothing for an attacker to hit
    anyway. Re-enable before bin/wake.sh restores the Fargate task.
  EOT
  type        = bool
  default     = true
}

variable "malai_api_container_port" {
  description = "Port the malai-api container listens on. Must match the value in ecs-malai-api — the VPC Link SG rules and the Cloud Map SRV port both key on it."
  type        = number
  default     = 8080
}

#
# Alerting (alarms.tf). The 5xx alarm and its gate moved here from
# ecs-malai-api when the ALB was replaced by Cloud Map: the metric is now
# `AWS/ApiGateway` `5xx`, dimensioned on the `ApiId` this unit owns.
#
variable "api_5xx_threshold" {
  description = "HTTP API 5xx responses per 5-minute window before paging. Covers both app-returned 5xx and API Gateway failing to reach a healthy task."
  type        = number
  default     = 5
}

variable "enable_request_alarms" {
  description = <<-EOT
    Whether to provision the HTTP API 5xx alarm.

    Set false in dev when the ECS service is intentionally asleep most of the
    time — every probe request returns 503 while there are no healthy Cloud
    Map instances, which would otherwise spam the alert. Prod must keep this
    true.
  EOT
  type        = bool
  default     = true
}
