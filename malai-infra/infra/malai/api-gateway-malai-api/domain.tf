#
# Custom domain — owned by CloudFront (see cloudfront.tf).
#
# Why CloudFront fronts the HTTP API rather than an API Gateway custom domain:
#   - WAFv2 doesn't support HTTP API v2 stages directly (only REST API, ALB,
#     CloudFront, AppSync, Cognito). Without CloudFront the WebACL had to live
#     on the ALB, where per-IP rules see the VPC Link's IP rather than the
#     real client. CloudFront brings rate limit + IP reputation back online.
#   - Edge-level rejection: bad payloads never traverse our infra.
#   - Lets us strip API Gateway response headers (`x-amz-apigw-id`,
#     `x-amzn-requestid`) via a Response Headers Policy.
#
# Caveat: the regional `{api-id}.execute-api.us-east-1.amazonaws.com` endpoint
# stays publicly reachable — anyone who finds the random API id could bypass
# CloudFront/WAF. Hardening (signed origin headers / VPC endpoint) tracked in
# MODERNIZATION.md.
#
resource "aws_acm_certificate" "this" {
  domain_name       = var.malai_api_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = local.tags
}

resource "aws_route53_record" "cert_validation" {
  name    = tolist(aws_acm_certificate.this.domain_validation_options)[0].resource_record_name
  type    = tolist(aws_acm_certificate.this.domain_validation_options)[0].resource_record_type
  zone_id = data.aws_route53_zone.root_domain.zone_id
  records = [tolist(aws_acm_certificate.this.domain_validation_options)[0].resource_record_value]
  ttl     = 60
}

resource "aws_acm_certificate_validation" "this" {
  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [aws_route53_record.cert_validation.fqdn]

  timeouts {
    create = "30m"
  }
}

resource "aws_route53_record" "this" {
  name    = var.malai_api_domain
  type    = "A"
  zone_id = data.aws_route53_zone.root_domain.id

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}
