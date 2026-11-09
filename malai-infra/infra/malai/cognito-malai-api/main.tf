locals {
  name_prefix = "${var.application}-${var.env}"
  tags = {
    Environment = var.env
    Terraform   = true
    infra-unit  = "cognito-malai-api"
    Apps        = join(" ", [var.app_mal, var.app_malai_api])
  }
}

resource "aws_cognito_user_pool" "this" {
  name = "${local.name_prefix}-malai-api-user-pool"

  # INFRA-11: Plus tier is required for `user_pool_add_ons.advanced_security_mode`
  # (compromised-credentials + adaptive auth). AWS provider v6 made the tier a
  # first-class attribute; without it the pool defaults to ESSENTIALS and apply
  # fails: FeatureUnavailableInTierException. Pricing: ~$0.05/MAU on first 50K
  # MAUs (rounds to zero at demo scale).
  user_pool_tier = "PLUS"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  # Passwordless cutover phase one (.scratch/passwordless-auth): EMAIL_OTP joins
  # PASSWORD as an allowed first factor. Additive — the password factor and
  # password app-client flows are removed in the cleanup slice once backend +
  # webapp are passwordless. EMAIL_OTP first factors are incompatible with
  # mfa_configuration = "REQUIRED"; this pool is OPTIONAL.
  sign_in_policy {
    allowed_first_auth_factors = ["PASSWORD", "EMAIL_OTP"]
  }

  verification_message_template {
    default_email_option = "CONFIRM_WITH_CODE"
  }

  email_configuration {
    email_sending_account = "DEVELOPER"
    from_email_address    = var.malai_api_cognito_from_email_address
    source_arn            = data.aws_ses_domain_identity.domain_identity.arn
  }

  password_policy {
    minimum_length    = 12
    require_lowercase = true
    require_numbers   = true
    require_symbols   = true
    require_uppercase = true

    temporary_password_validity_days = 7
  }

  username_configuration {
    case_sensitive = false
  }

  lambda_config {
    custom_message = aws_lambda_function.custom_message.arn
  }

  # INFRA-11 (D-06): enable Cognito Plus tier advanced security features.
  # ENFORCED runs compromised-credentials check + risk-based adaptive auth on
  # every sign-in. Pricing: ~$0.05/MAU on the first 50K MAUs (Plus tier).
  # At demo scale (<=10 MAU) monthly cost rounds to zero.
  user_pool_add_ons {
    advanced_security_mode = "ENFORCED"
  }

  # INFRA-11 (D-07): MFA available but not forced. Demo customer is not
  # enrolled, so the demo flow stays SMS/TOTP-free. Bank evaluators see
  # "MFA is configured at the IdP layer" without watching a TOTP screen
  # during a 20-minute pitch. SMS path intentionally omitted (would
  # require an SNS IAM role + per-attempt SMS spend with no demo benefit).
  mfa_configuration = "OPTIONAL"

  software_token_mfa_configuration {
    enabled = true
  }

  tags = local.tags
}

# Phase 2 D-2.10: RBAC source-of-truth — four user groups.
# Sarah Mitchell (the demo customer) is added to the `customer` group by the
# Wave-E seeder via the Cognito Admin API. The `manager` group (ADR-0010) gates
# the Exec surface and nothing else; the seeder adds a manager-only persona to it.
locals {
  cognito_groups = {
    customer      = "Retail customers — sees their own accounts and can transfer."
    support_agent = "Support agents — read-only across customer accounts. Cannot transfer."
    manager       = "Managers — the Exec metrics surface only. Cannot rule on money-movement decisions."
    admin         = "Admin — full access including admin-only endpoints. Out of demo path."
  }
}

resource "aws_cognito_user_group" "this" {
  for_each     = local.cognito_groups
  name         = each.key
  user_pool_id = aws_cognito_user_pool.this.id
  description  = each.value
  precedence   = lookup({ admin = 1, manager = 2, support_agent = 3, customer = 4 }, each.key)
}

resource "aws_cognito_user_pool_client" "this" {
  name         = "${local.name_prefix}-malai-api-user-pool-client"
  user_pool_id = aws_cognito_user_pool.this.id

  generate_secret = true

  explicit_auth_flows = [
    "ALLOW_USER_AUTH",
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]
}

resource "aws_lambda_permission" "custom_message" {
  statement_id  = "AllowExecutionFromCognito"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.custom_message.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.this.arn
  depends_on    = [aws_lambda_function.custom_message]
}

resource "aws_lambda_function" "custom_message" {
  function_name    = "${local.name_prefix}-malai-api-cognito-custom-message-lambda"
  filename         = data.archive_file.lambda.output_path
  role             = aws_iam_role.custom_message_lambda.arn
  handler          = "cognito_custom_message_lambda.handler"
  source_code_hash = filebase64sha256(data.archive_file.lambda.output_path)
  runtime          = "nodejs24.x"

  tags = local.tags
}

data "archive_file" "lambda" {
  type        = "zip"
  source_dir  = "${path.module}/lambda"
  output_path = "lambda.zip"
}

resource "aws_iam_role_policy_attachment" "custom_message_lambda_logs" {
  role       = aws_iam_role.custom_message_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role" "custom_message_lambda" {
  name = "${local.name_prefix}-malai-api-cognito-custom-message-lambda-role"

  assume_role_policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Effect": "Allow",
      "Sid": ""
    }
  ]
}
EOF
}

# Consumers (ecs-malai-api) read this by name. Naming convention:
# `${name_prefix}-malai-api-${integration}`. Each third-party integration owns
# one secret with a JSON body whose fields map 1:1 to env vars in the task def.
resource "aws_secretsmanager_secret" "client" {
  name       = "${local.name_prefix}-malai-api-cognito"
  kms_key_id = data.aws_kms_alias.secrets.target_key_arn
  tags       = local.tags
}

resource "aws_secretsmanager_secret_version" "client" {
  secret_id = aws_secretsmanager_secret.client.id
  secret_string = jsonencode({
    client_id     = aws_cognito_user_pool_client.this.id
    client_secret = aws_cognito_user_pool_client.this.client_secret
    issuer        = "https://cognito-idp.${var.region}.amazonaws.com/${aws_cognito_user_pool.this.id}"
  })
}
