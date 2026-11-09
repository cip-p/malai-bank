data "aws_route53_zone" "root_domain" {
  name         = var.root_public_domain
  private_zone = false
}
