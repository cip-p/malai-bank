output "db_address" {
  value = aws_db_instance.postgres.address
}

output "db_port" {
  value = aws_db_instance.postgres.port
}

output "db_name" {
  value = aws_db_instance.postgres.db_name
}

output "db_security_group_id" {
  value = aws_security_group.postgres.id
}

output "db_master_secret_arn" {
  # try() so terraform doesn't blow up at plan time when the modification to
  # enable manage_master_user_password is queued but not yet applied (the list
  # is empty until AWS reports the secret back).
  value       = try(aws_db_instance.postgres.master_user_secret[0].secret_arn, null)
  description = "ARN of the RDS-managed master credentials secret in Secrets Manager."
}

output "app_db_secret_arn" {
  value       = aws_secretsmanager_secret.app_db.arn
  description = "ARN of the malai_app runtime credentials secret. Consumed by ecs-malai-api and ecs-malai-seeder."
}
