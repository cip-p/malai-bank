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

variable "malai_api_ses_domain_identity" {
  description = "The domain name used for sending emails for Malai API"
}

variable "malai_api_cognito_from_email_address" {
  description = "A friendly name for the email sender in the format 'John Stiles <johnstiles@example.com>'."
}
