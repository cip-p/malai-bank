locals {
  name_prefix = "${var.application}-${var.env}"

  tags = {
    Environment = var.env
    Project     = var.application
    Terraform   = true
    infra-unit  = "kms"
  }
}

data "aws_caller_identity" "current" {}

#
# Customer-managed key for Secrets Manager values (db_master, cognito client, etc).
# Default key policy: only the account root has rights. Consumer modules grant
# their IAM principals `kms:Decrypt` on this key as needed (see ecs-malai-api).
#
resource "aws_kms_key" "secrets" {
  description             = "${local.name_prefix} Secrets Manager values"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.tags
}

resource "aws_kms_alias" "secrets" {
  name          = "alias/${local.name_prefix}-secrets"
  target_key_id = aws_kms_key.secrets.key_id
}

#
# Customer-managed key for CloudWatch Logs (ECS task log group, lambda log groups).
# CloudWatch Logs encrypts log payloads transparently; the service principal
# `logs.${region}.amazonaws.com` is granted on the KEY POLICY here (it can't
# assume an IAM role). Consumer modules just set `kms_key_id` on their log
# groups and need no extra IAM grants.
#
# Reference: https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/encrypt-log-data-kms.html
#
resource "aws_kms_key" "logs" {
  count = var.enable_logs_kms_key ? 1 : 0

  description             = "${local.name_prefix} CloudWatch Logs"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  policy = data.aws_iam_policy_document.logs_key.json

  tags = local.tags
}

resource "aws_kms_alias" "logs" {
  count = var.enable_logs_kms_key ? 1 : 0

  name          = "alias/${local.name_prefix}-logs"
  target_key_id = aws_kms_key.logs[0].key_id
}

data "aws_iam_policy_document" "logs_key" {
  # Standard root statement so IAM continues to govern access.
  statement {
    sid    = "EnableIAMUserPermissions"
    effect = "Allow"
    principals {
      type        = "AWS"
      identifiers = ["arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"]
    }
    actions   = ["kms:*"]
    resources = ["*"]
  }

  # CloudWatch Logs needs to encrypt log data with this key. Scoped via the
  # `kms:EncryptionContext:aws:logs:arn` condition to log groups in this account
  # and region — without this, any service in the account could ask the key to
  # encrypt data on behalf of an arbitrary log group.
  statement {
    sid    = "AllowCloudWatchLogs"
    effect = "Allow"
    principals {
      type        = "Service"
      identifiers = ["logs.${var.region}.amazonaws.com"]
    }
    actions = [
      "kms:Encrypt*",
      "kms:Decrypt*",
      "kms:ReEncrypt*",
      "kms:GenerateDataKey*",
      "kms:Describe*",
    ]
    resources = ["*"]
    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:*"]
    }
  }
}
