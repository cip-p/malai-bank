output "secrets_kms_key_arn" {
  value       = aws_kms_key.secrets.arn
  description = "ARN of the CMK protecting Secrets Manager values."
}

output "secrets_kms_key_alias" {
  value       = aws_kms_alias.secrets.name
  description = "Alias of the secrets CMK (consumers look it up via aws_kms_alias data source)."
}

output "logs_kms_key_arn" {
  value       = one(aws_kms_key.logs[*].arn)
  description = "ARN of the CMK protecting CloudWatch Logs. Null when var.enable_logs_kms_key is false."
}

output "logs_kms_key_alias" {
  value       = one(aws_kms_alias.logs[*].name)
  description = "Alias of the logs CMK. Null when var.enable_logs_kms_key is false."
}
