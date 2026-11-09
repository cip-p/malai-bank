#
# CloudWatch alarms for the public ACM certificate fronting the malai-website
# CloudFront distribution. Three thresholds (30 / 14 / 7 days) all wired to
# the shared `${name_prefix}-malai-api-alerts` SNS topic.
#
# The cert is declared inside module.malai-website (modules/malai-cloudfront-web);
# the consuming dimension reads module.malai-website.acm_certificate_arn -- a
# new output added in this phase per Plan 03 Task 1.
#
# main.tf already declares `data "aws_sns_topic" "alerts"` and does not declare
# a `locals` block, so this file declares `locals { name_prefix, tags, ... }`
# locally and references the existing data block.
#
# All public certs are us-east-1; no provider alias needed. Per D-13 / INFRA-07.
#

locals {
  name_prefix = "${var.application}-${var.env}"
  tags = {
    Environment = var.env
    Terraform   = true
    infra-unit  = "cloudfront-malai-website"
    Apps        = join(" ", [var.app_mal, var.app_malai_website])
  }
  cert_expiry_thresholds = {
    "30days" = 30
    "14days" = 14
    "7days"  = 7
  }
}

resource "aws_cloudwatch_metric_alarm" "tls_expiry" {
  for_each = var.malai_website_alarms_enabled ? local.cert_expiry_thresholds : {}

  alarm_name          = "${local.name_prefix}-malai-website-tls-expiry-${each.key}"
  alarm_description   = "ACM cert <${each.value} days from expiry on the malai-website CloudFront distribution"
  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "DaysToExpiry"
  namespace           = "AWS/CertificateManager"
  period              = 86400
  statistic           = "Minimum"
  threshold           = each.value
  treat_missing_data  = "notBreaching"

  dimensions = {
    CertificateArn = module.malai-website.acm_certificate_arn
  }

  alarm_actions = [data.aws_sns_topic.alerts[0].arn]
  ok_actions    = [data.aws_sns_topic.alerts[0].arn]
  tags          = local.tags
}
