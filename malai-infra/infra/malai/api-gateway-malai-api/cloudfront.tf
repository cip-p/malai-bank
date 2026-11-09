#
# CloudFront distribution in front of the HTTP API v2 regional endpoint.
#
# This is the public face of the API. Clients hit `api.app.fcd8ayxqr72.com`
# (Route 53 alias → CloudFront), CloudFront forwards to the API Gateway
# regional `execute-api` endpoint, which goes through VPC Link → ALB → ECS.
#
# WAF (CLOUDFRONT-scope WebACL in waf.tf) is attached here. Edge protections
# (rate limit, IP reputation, OWASP) all see real client IPs.
#
# Cache behaviour: nothing is cached. Every request is dynamic and personal —
# we use the AWS-managed `CachingDisabled` policy. No need for invalidations,
# no risk of returning a stale response to the wrong user.
#

locals {
  # AWS-managed CloudFront policy IDs (stable, documented).
  # https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-managed-cache-policies.html
  cloudfront_managed_cache_policy_disabled                    = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
  cloudfront_managed_origin_request_policy_all_viewer_no_host = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
}

#
# Strip API Gateway fingerprint headers from responses where CloudFront lets
# us. There are two layers of un-strippable headers we have to live with:
#
#   1. CloudFront's reserved RemoveHeaders allowlist — `x-amzn-requestid`,
#      `x-amzn-trace-id`, and several others can't be removed via this policy
#      (CloudFront returns InvalidArgument). Lambda@Edge could remove them
#      but adds significant cost and complexity for marginal opacity benefit.
#   2. CloudFront's own response headers (`x-amz-cf-id`, `via`, `x-amz-cf-pop`,
#      `x-cache`) are added AFTER the policy runs and can never be stripped
#      from a CloudFront-fronted response. They identify the platform, not us.
#
# What we *can* strip: `x-amz-apigw-id`. That's the strongest API-Gateway-
# specific fingerprint so it's the most useful to remove.
#
resource "aws_cloudfront_response_headers_policy" "api" {
  name    = "${local.name_prefix}-malai-api-strip-aws-headers"
  comment = "Strip API Gateway fingerprint headers from origin responses"

  remove_headers_config {
    # HTTP API v2 emits this; REST v1 emitted `x-amz-apigw-id` (different
    # header). We're on v2, so this is the one we strip.
    items {
      header = "apigw-requestid"
    }
  }
}

resource "aws_cloudfront_distribution" "this" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "${local.name_prefix} malai-api"
  aliases         = [var.malai_api_domain]
  http_version    = "http2and3"

  # PriceClass_100 = North America + Europe edge locations. PriceClass_All
  # adds APAC/SA — bump if you start serving traffic from those regions.
  price_class = "PriceClass_100"

  # CLOUDFRONT-scope WebACL from waf.tf. Note this takes the ARN, not the ID,
  # despite the field being named `web_acl_id` (legacy naming). `null` when
  # `var.enable_waf = false` (deep-sleep mode); CloudFront treats null as
  # "no WAF attached".
  web_acl_id = var.enable_waf ? aws_wafv2_web_acl.this[0].arn : null

  origin {
    domain_name = "${aws_apigatewayv2_api.this.id}.execute-api.${var.region}.amazonaws.com"
    origin_id   = "apigatewayv2"

    # Stage is `$default` (see main.tf) — no path prefix on the regional URL,
    # so CloudFront forwards `/version` straight through to the API.

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "apigatewayv2"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    cache_policy_id            = local.cloudfront_managed_cache_policy_disabled
    origin_request_policy_id   = local.cloudfront_managed_origin_request_policy_all_viewer_no_host
    response_headers_policy_id = aws_cloudfront_response_headers_policy.api.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate.this.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = local.tags

  depends_on = [aws_acm_certificate_validation.this]
}
