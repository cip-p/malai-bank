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
  description = "The name of the MalaiBank application"
}
