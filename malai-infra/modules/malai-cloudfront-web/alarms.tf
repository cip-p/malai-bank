#
# CloudWatch alarms for the distribution.
#
# All CloudFront metrics live in the `Global` region (CloudFront is a global
# AWS service). The alarm resource itself sits in whichever region the
# provider points at; we deploy everything in us-east-1 so no provider alias
# is needed. The metric `Region` *dimension* must be `Global` regardless of
# where the alarm lives.
#
# Wired to the SNS topic created in `ecs-malai-api/alarms.tf` (so static-site
# failures land in the same inbox as backend failures). Set
# `var.alarms_sns_topic_arn = ""` to skip alarms entirely.
#
# We deliberately *don't* alarm on CacheHitRate in dev — low ambient traffic
# makes the percentage swing wildly and most evaluations would be on too few
# samples. Re-evaluate for prod once we have a baseline.
#

#
# 5xxErrorRate. CloudFront reports the metric as a percentage of total
# requests so the threshold is in percent (default 1 = 1%). Two consecutive
# 5-min periods above the threshold trips the alarm — single transient spikes
# don't page.
#
# `treat_missing_data = "notBreaching"` keeps the alarm OK when the
# distribution is idle (zero requests → no metric → no alarm). Without this,
# an idle dev distribution would alarm INSUFFICIENT_DATA constantly.
#
resource "aws_cloudwatch_metric_alarm" "cloudfront_5xx" {
  count = var.alarms_sns_topic_arn != "" ? 1 : 0

  alarm_name          = "${local.name_prefix}-${var.app_identifier}-cloudfront-5xx"
  alarm_description   = "CloudFront 5xx error rate above ${var.cloudfront_5xx_threshold_percent}% for ${var.app_identifier}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "5xxErrorRate"
  namespace           = "AWS/CloudFront"
  period              = 300
  statistic           = "Average"
  threshold           = var.cloudfront_5xx_threshold_percent
  treat_missing_data  = "notBreaching"

  dimensions = {
    DistributionId = aws_cloudfront_distribution.this.id
    Region         = "Global"
  }

  alarm_actions = [var.alarms_sns_topic_arn]
  ok_actions    = [var.alarms_sns_topic_arn]
  tags          = local.tags
}
