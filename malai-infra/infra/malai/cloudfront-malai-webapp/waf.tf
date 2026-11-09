#
# WAFv2 in front of the malai-webapp CloudFront distribution.
#
# Why on the webapp specifically (and not the static marketing site): the
# webapp serves auth flows — login, password reset, Cognito redirects. That
# makes it a credential-stuffing target. The website is read-only marketing
# pages and doesn't host any auth surface, so the WAF spend isn't justified
# there.
#
# Same rule shape as `api-gateway-malai-api/waf.tf` so the two distributions
# present a consistent attack surface — common rule set, known-bad-inputs,
# Amazon IP reputation, and a per-IP rate limit. CLOUDFRONT-scope WebACLs
# are global and must be created in us-east-1; we're already there so no
# provider alias needed.
#
# Cost: ~$5/mo base + $1/mo per managed rule group + $0.60 per million
# requests evaluated.
#

resource "aws_wafv2_web_acl" "webapp" {
  count = var.enable_waf ? 1 : 0

  name        = "${var.application}-${var.env}-malai-webapp"
  description = "WAF for malai-webapp CloudFront distribution"
  scope       = "CLOUDFRONT"

  default_action {
    allow {}
  }

  # OWASP-style core protections (XSS, SQLi, LFI, command injection, ...).
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

  # AWS-curated patterns of known-bad request shapes (Log4Shell, etc.).
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

  # Per-IP rate limit. 2000 requests / 5 min ≈ 6 req/sec sustained — fine for
  # a human-driven banking webapp, brutal for a credential-stuffing botnet
  # walking down a list of usernames. Bump if legitimate bursts exceed.
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
    metric_name                = "${var.application}-${var.env}-malai-webapp"
    sampled_requests_enabled   = true
  }

  tags = {
    Environment = var.env
    Terraform   = true
    infra-unit  = "cloudfront-malai-webapp"
    Apps        = join(" ", [var.app_mal, var.app_malai_webapp])
  }
}
