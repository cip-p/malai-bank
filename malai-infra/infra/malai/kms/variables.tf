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

variable "enable_logs_kms_key" {
  description = <<-EOT
    Provision the CloudWatch Logs CMK. Its only consumers are the log groups
    owned by the backend units (ecs-malai-api, ecs-malai-seeder, ecs-malai-stub,
    api-gateway-malai-api), so with the backend torn down the key is $1/mo for
    nothing. Set false to drop it; flip back to true and apply this unit before
    re-applying the backend.

    Note the secrets CMK is deliberately not gated the same way — cognito-malai-api
    survives the teardown and its secrets are encrypted with it.
  EOT
  type        = bool
  default     = true
}
