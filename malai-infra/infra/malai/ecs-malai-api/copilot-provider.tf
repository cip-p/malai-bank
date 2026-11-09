#
# The Copilot's real-provider key.
#
# The Copilot invokes a model through a Decider seam whose default implementation
# is deterministic and needs no credential at all. Selecting the real provider
# (`copilot_decider = "anthropic"`) is what creates this secret, wires it into the
# task as `MAL_COPILOT_ANTHROPIC_API_KEY`, and grants the execution role read
# access — so an environment left on the default has no dangling provider secret
# and the malai-api container boots without one.
#
# malai-backend never names a key. The property the backend binds
# (`malai.copilot.anthropic.api-key`) appears in its configuration only as this
# env var reference; no value for it exists in that repo.
#
# Created with a placeholder; populate the real key manually via the AWS console
# after `terraform apply`. `secret_string` is in `ignore_changes` so subsequent
# applies don't overwrite the manual update. No rotation; manual console
# management only, same posture as the end-to-end test-user secrets.
#
locals {
  copilot_provider_enabled = var.copilot_decider == "anthropic"
}

resource "aws_secretsmanager_secret" "copilot_anthropic_api_key" {
  count = local.copilot_provider_enabled ? 1 : 0

  name        = "${local.name_prefix}-malai-api-copilot-anthropic-api-key"
  description = "API key the Copilot's Anthropic Decider adapter authenticates with."
  kms_key_id  = data.aws_kms_alias.secrets.target_key_arn

  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "copilot_anthropic_api_key_initial" {
  count = local.copilot_provider_enabled ? 1 : 0

  secret_id = aws_secretsmanager_secret.copilot_anthropic_api_key[0].id
  secret_string = jsonencode({
    api_key = "REPLACE_ME_VIA_CONSOLE"
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}
