#
# Alerting: SNS topic + CloudWatch alarms for ECS / ALB / RDS / app log errors.
#
# Everything funnels into one SNS topic per env (`${name_prefix}-malai-api-alerts`).
# Subscribers are added via `var.alert_emails` — leave empty in tfvars to provision
# the topic without subscriptions (we still want alarms firing into the topic so
# they show up in the AWS console). Email subscriptions need manual confirmation
# the first time; Terraform creates them but the recipient must click the link.
#
# Thresholds are intentionally conservative for dev — they should fire on
# anything actually unusual, not just normal load. Tighten in prod.tfvars when
# we have real traffic baselines.
#

resource "aws_sns_topic" "alerts" {
  name              = "${local.name_prefix}-malai-api-alerts"
  kms_master_key_id = "alias/aws/sns" # AWS-managed CMK is fine for alert payloads
  tags              = local.tags
}

# Topic policy — allow CloudWatch to publish. Without this, alarms in OK/ALARM
# state transitions can't deliver. Restrict to alarms in this account.
data "aws_iam_policy_document" "alerts_topic" {
  statement {
    sid    = "AllowCloudWatchAlarmsPublish"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }
    actions   = ["SNS:Publish"]
    resources = [aws_sns_topic.alerts.arn]
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_sns_topic_policy" "alerts" {
  arn    = aws_sns_topic.alerts.arn
  policy = data.aws_iam_policy_document.alerts_topic.json
}

# Email subscribers. Each address gets a confirmation email from AWS the first
# time — Terraform marks the subscription as `pending confirmation` until that
# link is clicked. Use a distribution list, not a personal inbox, for prod.
resource "aws_sns_topic_subscription" "alerts_email" {
  for_each  = toset(var.alert_emails)
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = each.value
}

#
# Application log alarm: count ERROR-level entries from the JSON logs and fire
# if more than `var.error_log_threshold` land in a 5-minute window.
#
# The metric filter pattern `{ $.level = "ERROR" }` matches the `level` field
# emitted by the Logstash Logback encoder (see malai-api/logback-spring.xml).
# If you switch the encoder field name, update this filter or the metric goes
# silently to zero.
#
resource "aws_cloudwatch_log_metric_filter" "errors" {
  name           = "${local.name_prefix}-malai-api-errors"
  log_group_name = aws_cloudwatch_log_group.task.name
  pattern        = "{ $.level = \"ERROR\" }"

  metric_transformation {
    name          = "${local.name_prefix}-malai-api-error-count"
    namespace     = "Malai/Application"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_metric_alarm" "app_errors" {
  alarm_name          = "${local.name_prefix}-malai-api-error-rate"
  alarm_description   = "ERROR-level log lines from malai-api in the last 5 minutes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = aws_cloudwatch_log_metric_filter.errors.metric_transformation[0].name
  namespace           = aws_cloudwatch_log_metric_filter.errors.metric_transformation[0].namespace
  period              = 300
  statistic           = "Sum"
  threshold           = var.error_log_threshold
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
  tags                = local.tags
}

#
# 5xx coverage moved to api-gateway-malai-api/alarms.tf when the internal ALB
# was removed in favour of Cloud Map service discovery.
#
# The two alarms that used to live here watched `AWS/ApplicationELB`
# `HTTPCode_Target_5XX_Count` (the app returned 5xx) and `HTTPCode_ELB_5XX_Count`
# (the LB had no healthy target). With no load balancer those metrics no longer
# exist. `AWS/ApiGateway` `5xx` on the HTTP API covers both conditions from one
# hop further out — and covers strictly more, because it also catches
# integration failures the ALB never saw. The api-gateway unit owns the alarm
# because it owns the `ApiId` dimension; it publishes into this same SNS topic
# via a data lookup.
#
# Note this unit no longer publishes a "no healthy tasks" signal of its own.
# `RunningTaskCount` would be the natural metric but it's only emitted under
# Container Insights, which is disabled on the cluster (~$2-5/mo). The API
# Gateway 5xx alarm is the free equivalent: no healthy Cloud Map instance means
# API Gateway returns 503, which trips it.
#

#
# ECS service health: CPU / memory utilisation. ECS publishes these per
# (ClusterName, ServiceName). Bump thresholds in prod.tfvars once we have
# real load data — for now 80% sustained for 10 min is a clear "investigate".
#
resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  alarm_name          = "${local.name_prefix}-malai-api-ecs-cpu-high"
  alarm_description   = "ECS service CPU utilisation high (sustained)"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = var.ecs_cpu_threshold
  treat_missing_data  = "notBreaching"

  dimensions = {
    ClusterName = aws_ecs_cluster.this.name
    ServiceName = aws_ecs_service.this.name
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
  tags          = local.tags
}

resource "aws_cloudwatch_metric_alarm" "ecs_memory_high" {
  alarm_name          = "${local.name_prefix}-malai-api-ecs-memory-high"
  alarm_description   = "ECS service memory utilisation high (sustained)"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 300
  statistic           = "Average"
  threshold           = var.ecs_memory_threshold
  treat_missing_data  = "notBreaching"

  dimensions = {
    ClusterName = aws_ecs_cluster.this.name
    ServiceName = aws_ecs_service.this.name
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
  tags          = local.tags
}

#
# RDS health. We only run one Postgres instance per env, so dimensions key on
# its identifier (= name_prefix per the rds module).
#
# Storage alarm uses bytes — converted from GB threshold via the data source's
# allocated_storage. CPU and connections are straight percentage / count.
#
resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name          = "${local.name_prefix}-malai-api-rds-cpu-high"
  alarm_description   = "RDS CPU utilisation high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = var.rds_cpu_threshold
  treat_missing_data  = "notBreaching"

  dimensions = {
    DBInstanceIdentifier = data.aws_db_instance.postgres.db_instance_identifier
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
  tags          = local.tags
}

resource "aws_cloudwatch_metric_alarm" "rds_connections_high" {
  alarm_name          = "${local.name_prefix}-malai-api-rds-connections-high"
  alarm_description   = "RDS connection count high — Hikari leak or runaway client"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseConnections"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = var.rds_connections_threshold
  treat_missing_data  = "notBreaching"

  dimensions = {
    DBInstanceIdentifier = data.aws_db_instance.postgres.db_instance_identifier
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
  tags          = local.tags
}

# `FreeStorageSpace` is in bytes; alarm fires when free space drops below
# 20% of allocated. AWS docs recommend keeping at least 10–15% free for autoscaling
# headroom, so 20% gives us time to react.
resource "aws_cloudwatch_metric_alarm" "rds_storage_low" {
  alarm_name          = "${local.name_prefix}-malai-api-rds-storage-low"
  alarm_description   = "RDS free storage below 20% of allocated"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = data.aws_db_instance.postgres.allocated_storage * 1024 * 1024 * 1024 * 0.2
  treat_missing_data  = "notBreaching"

  dimensions = {
    DBInstanceIdentifier = data.aws_db_instance.postgres.db_instance_identifier
  }

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
  tags          = local.tags
}
