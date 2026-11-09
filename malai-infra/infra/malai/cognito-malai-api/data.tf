data "aws_ses_domain_identity" "domain_identity" {
  domain = var.malai_api_ses_domain_identity
}

# Owned by the kms module — same CMK that encrypts the db_master secret.
data "aws_kms_alias" "secrets" {
  name = "alias/${local.name_prefix}-secrets"
}
