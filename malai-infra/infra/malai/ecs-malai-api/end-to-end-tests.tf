#
# Credentials for the end-to-end test suites.
#
# Two seeded Cognito users, one secret each, same posture (per ADR-0002 the
# seeded password path is the deliberately retained test login mechanism):
#
#   - end-to-end-test-user          the shared evaluator (customer estate)
#   - end-to-end-ops-reviewer-user  the ops reviewer (`admin` group, no estate)
#
# Dev only. Each secret is created with a placeholder; populate the real values
# manually via the AWS console after `terraform apply` — the seeded email and
# demo password live as constants in the malai-backend seeder (ops reviewer:
# `malai-seeder .../data/OpsReviewerSeed.kt`, EMAIL / DEMO_PASSWORD).
# `secret_string` is in `ignore_changes` so subsequent applies don't overwrite
# the manual update. No rotation; manual console management only.
#
# The webapp Playwright suite reads these as JSON `{username, password}`, with
# env overrides as the bypass: MALAI_E2E_EMAIL / MALAI_E2E_PASSWORD for the
# evaluator, MALAI_E2E_OPS_EMAIL / MALAI_E2E_OPS_PASSWORD for the ops reviewer.
#
resource "aws_secretsmanager_secret" "end_to_end_test_user" {
  count = var.env == "dev" ? 1 : 0

  name        = "${local.name_prefix}-malai-api-end-to-end-test-user"
  description = "Pre-existing Cognito user used by end-to-end tests."
  kms_key_id  = data.aws_kms_alias.secrets.target_key_arn

  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "end_to_end_test_user_initial" {
  count = var.env == "dev" ? 1 : 0

  secret_id = aws_secretsmanager_secret.end_to_end_test_user[0].id
  secret_string = jsonencode({
    username = "REPLACE_ME_VIA_CONSOLE"
    password = "REPLACE_ME_VIA_CONSOLE"
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_secretsmanager_secret" "end_to_end_ops_reviewer_user" {
  count = var.env == "dev" ? 1 : 0

  name        = "${local.name_prefix}-malai-api-end-to-end-ops-reviewer-user"
  description = "Seeded ops reviewer (admin group, no customer estate) used by end-to-end tests."
  kms_key_id  = data.aws_kms_alias.secrets.target_key_arn

  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "end_to_end_ops_reviewer_user_initial" {
  count = var.env == "dev" ? 1 : 0

  secret_id = aws_secretsmanager_secret.end_to_end_ops_reviewer_user[0].id
  secret_string = jsonencode({
    username = "REPLACE_ME_VIA_CONSOLE"
    password = "REPLACE_ME_VIA_CONSOLE"
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}
