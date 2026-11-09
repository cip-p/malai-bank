#
# CloudWatch alarms for the public ACM certificate fronting the malai-api
# CloudFront distribution. Three thresholds (30 / 14 / 7 days) all wired to
# the shared `${name_prefix}-malai-api-alerts` SNS topic owned by
# ecs-malai-api/alarms.tf.
#
# DaysToExpiry is published by ACM in the same region as the cert. All public
# certs in this stack live in us-east-1 (CloudFront viewer-cert constraint),
# so no provider alias is needed. If a future cert is issued elsewhere, that
# unit needs its own alarms.tf with the right provider alias.
#
# Per CONTEXT.md D-13 / INFRA-07. Pattern shape from
# modules/malai-cloudfront-web/alarms.tf and ecs-malai-api/alarms.tf.
#

# Reuse the SNS topic created by ecs-malai-api/alarms.tf -- same lookup pattern
# as cloudfront-malai-webapp/main.tf line 4-6.
data "aws_sns_topic" "alerts" {
  name = "${var.application}-${var.env}-malai-api-alerts"
}

locals {
  cert_expiry_thresholds = {
    "30days" = 30
    "14days" = 14
    "7days"  = 7
  }
}

#
# HTTP API 5xx. Replaces the two `AWS/ApplicationELB` alarms that lived in
# ecs-malai-api before the internal ALB was removed — see the note there.
#
# One alarm covers what took two: `HTTPCode_Target_5XX_Count` (the app itself
# returned 5xx) and `HTTPCode_ELB_5XX_Count` (nothing healthy to route to) are
# both visible here as a 5xx at the gateway, along with integration failures
# the ALB never saw. The trade is that the alert no longer tells you which of
# the two it was; the ECS CPU/memory alarms and the app-error-rate alarm in
# ecs-malai-api/alarms.tf are the next place to look.
#
resource "aws_cloudwatch_metric_alarm" "api_5xx" {
  count = var.enable_request_alarms ? 1 : 0

  alarm_name          = "${local.name_prefix}-malai-api-5xx"
  alarm_description   = "5xx responses from the malai-api HTTP API (app error, or no healthy task to route to)"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "5xx"
  namespace           = "AWS/ApiGateway"
  period              = 300
  statistic           = "Sum"
  threshold           = var.api_5xx_threshold
  treat_missing_data  = "notBreaching"

  dimensions = {
    ApiId = aws_apigatewayv2_api.this.id
  }

  alarm_actions = [data.aws_sns_topic.alerts.arn]
  ok_actions    = [data.aws_sns_topic.alerts.arn]
  tags          = local.tags
}

resource "aws_cloudwatch_metric_alarm" "tls_expiry" {
  for_each = local.cert_expiry_thresholds

  alarm_name          = "${local.name_prefix}-malai-api-tls-expiry-${each.key}"
  alarm_description   = "ACM cert <${each.value} days from expiry on the malai-api CloudFront distribution"
  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "DaysToExpiry"
  namespace           = "AWS/CertificateManager"
  period              = 86400     # ACM publishes ~twice/day; 1-day period is stable
  statistic           = "Minimum" # take the worst recent reading
  threshold           = each.value
  treat_missing_data  = "notBreaching" # ACM stops publishing once the cert expires

  dimensions = {
    CertificateArn = aws_acm_certificate.this.arn
  }

  alarm_actions = [data.aws_sns_topic.alerts.arn]
  ok_actions    = [data.aws_sns_topic.alerts.arn]
  tags          = local.tags
}
