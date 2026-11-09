# After resources have been created, add the DNS records (found under "Authentication tab -> " "Publish DNS records", there must be 3) to the domain DNS custom records.

resource "aws_ses_domain_identity" "this" {
  domain = var.malai_api_ses_domain_identity
}

resource "aws_ses_domain_dkim" "this" {
  domain = aws_ses_domain_identity.this.domain
}
