#
# ACM certificate + Route 53 records.
#
# us-east-1 cert is required for CloudFront viewer certificates; assume the
# caller is configured for us-east-1 already.
#

resource "aws_acm_certificate" "this" {
  domain_name               = var.app_domain
  subject_alternative_names = var.enable_www_alias ? ["www.${var.app_domain}"] : null
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = local.tags
}

resource "aws_route53_record" "www" {
  for_each = {
    for dvo in aws_acm_certificate.this.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.root_domain.zone_id
}

resource "aws_route53_record" "this" {
  name    = var.app_domain
  type    = "A"
  zone_id = data.aws_route53_zone.root_domain.id

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "www_alias" {
  count   = var.enable_www_alias ? 1 : 0
  name    = "www.${var.app_domain}"
  type    = "A"
  zone_id = data.aws_route53_zone.root_domain.id

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_acm_certificate_validation" "cert" {
  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [for record in aws_route53_record.www : record.fqdn]
}
