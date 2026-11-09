#
# AWS WAFv2 in front of CloudFront (which fronts the HTTP API v2).
#
# Scope is CLOUDFRONT (vs REGIONAL on the previous ALB association). Two
# meaningful wins from this move:
#   - Per-IP rules (rate limit + IP reputation) see the real client IP again.
#     Previously, with the WAF on the ALB, those rules saw the VPC Link ENI's
#     IP and were effectively no-ops.
#   - Rule evaluation happens at the edge — bad payloads never reach the API
#     Gateway, ALB, or task. Lower load + lower bill on the inside.
#
# CLOUDFRONT-scope WebACLs are global and must be created in us-east-1. We're
# already in us-east-1 so no provider alias needed.
#
# Cost: ~$5/mo base + $1/mo per managed rule group + $0.60 per million requests.
#
resource "aws_wafv2_web_acl" "this" {
  count = var.enable_waf ? 1 : 0

  name        = "${local.name_prefix}-malai-api"
  description = "WAF for malai-api CloudFront distribution"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  # OWASP-style core protections (XSS, SQLi, LFI, PHP, NoSQL, command injection, ...)
  rule {
    name     = "AWS-AWSManagedRulesCommonRuleSet"
    priority = 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "common-rule-set"
      sampled_requests_enabled   = true
    }
  }

  # AWS-curated patterns of known-bad request shapes (Log4Shell, etc.)
  rule {
    name     = "AWS-AWSManagedRulesKnownBadInputsRuleSet"
    priority = 2

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "known-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  # Block IPs Amazon flags as low-reputation (botnets, scanners, exit nodes).
  # At CloudFront edge this finally sees real client IPs.
  rule {
    name     = "AWS-AWSManagedRulesAmazonIpReputationList"
    priority = 3

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesAmazonIpReputationList"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "ip-reputation"
      sampled_requests_enabled   = true
    }
  }

  # Per-IP rate limit. 2000 / 5 min works out to ~6 req/sec sustained — fine
  # for a banking app where most traffic is human-driven. Bump if legitimate
  # bursts (e.g. webhooks) exceed it.
  rule {
    name     = "RateLimitPerIP"
    priority = 4

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "rate-limit-per-ip"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.name_prefix}-malai-api"
    sampled_requests_enabled   = true
  }

  tags = local.tags
}

# CloudFront-scope WebACLs attach via the distribution's `web_acl_id` argument
# (see cloudfront.tf), not via aws_wafv2_web_acl_association — that's
# REGIONAL-only.
