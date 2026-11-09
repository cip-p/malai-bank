#
# Credentials for the post-deploy smoke test suite (`malai-backend/smoke-tests.sh`).
#
# The suite logs in as a real Cognito user and exercises a happy path through
# the auth flow + a couple of representative endpoints. We need a real account
# (because Cognito issues real JWTs) — this secret holds the test user's
# email/password for that pre-existing dev account.
#
# Dev only. The secret is created with a placeholder; populate the real values
# manually via the AWS console after a one-off Cognito sign-up. `secret_string`
# is in `ignore_changes` so subsequent terraform applies don't overwrite the
# manual update.
#
resource "aws_secretsmanager_secret" "smoke_test_user" {
  count = var.env == "dev" ? 1 : 0

  name        = "${local.name_prefix}-malai-api-smoke-test-user"
  description = "Pre-existing Cognito user used by post-deploy smoke tests."
  kms_key_id  = data.aws_kms_alias.secrets.target_key_arn

  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "smoke_test_user_initial" {
  count = var.env == "dev" ? 1 : 0

  secret_id = aws_secretsmanager_secret.smoke_test_user[0].id
  secret_string = jsonencode({
    username = "REPLACE_ME_VIA_CONSOLE"
    password = "REPLACE_ME_VIA_CONSOLE"
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}
