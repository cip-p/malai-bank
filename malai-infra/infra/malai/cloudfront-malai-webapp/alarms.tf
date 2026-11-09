#
# CloudWatch alarms for the public ACM certificate fronting the malai-webapp
# CloudFront distribution. Three thresholds (30 / 14 / 7 days) all wired to
# the shared `${name_prefix}-malai-api-alerts` SNS topic.
#
# The cert is declared inside module.malai-webapp (modules/malai-cloudfront-web);
# the consuming dimension reads module.malai-webapp.acm_certificate_arn -- a
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
    infra-unit  = "cloudfront-malai-webapp"
    Apps        = join(" ", [var.app_mal, var.app_malai_webapp])
  }
  cert_expiry_thresholds = {
    "30days" = 30
    "14days" = 14
    "7days"  = 7
  }
}

resource "aws_cloudwatch_metric_alarm" "tls_expiry" {
  for_each = var.malai_webapp_alarms_enabled ? local.cert_expiry_thresholds : {}

  alarm_name          = "${local.name_prefix}-malai-webapp-tls-expiry-${each.key}"
  alarm_description   = "ACM cert <${each.value} days from expiry on the malai-webapp CloudFront distribution"
  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "DaysToExpiry"
  namespace           = "AWS/CertificateManager"
  period              = 86400
  statistic           = "Minimum"
  threshold           = each.value
  treat_missing_data  = "notBreaching"

  dimensions = {
    CertificateArn = module.malai-webapp.acm_certificate_arn
  }

  alarm_actions = [data.aws_sns_topic.alerts[0].arn]
  ok_actions    = [data.aws_sns_topic.alerts[0].arn]
  tags          = local.tags
}
