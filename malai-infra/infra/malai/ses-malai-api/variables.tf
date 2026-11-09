variable "assume_role" {
  description = "Role to run infra-unit as. Shape matches the S3 backend `assume_role` block."
  type = object({
    role_arn = string
  })
}

variable "region" {
  description = "The AWS region"
}

variable "malai_api_ses_domain_identity" {
  description = "The domain name used for sending emails for Malai API"
}
