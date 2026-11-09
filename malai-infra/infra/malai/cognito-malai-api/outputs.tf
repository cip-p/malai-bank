output "user_pool_id" {
  value = aws_cognito_user_pool.this.id
}

output "user_pool_client_id" {
  value = aws_cognito_user_pool_client.this.id
}

# Per-service Cognito client ids, keyed by the service name in
# `local.services_requiring_service_jwt`. Consumers (e.g. ecs-malai-api) build env-var
# names from the same key, so a single map plays both sides of the wiring.
output "service_client_ids" {
  description = "Map of service name -> Cognito M2M client id for the service-to-service caller."
  value       = { for svc, client in aws_cognito_user_pool_client.service : svc => client.id }
}

output "service_secret_arns" {
  description = "Map of service name -> Secrets Manager ARN holding {client_id, client_secret, token_endpoint_url}."
  value       = { for svc, secret in aws_secretsmanager_secret.service : svc => secret.arn }
}

output "service_token_endpoint_url" {
  description = "Cognito OAuth2 token endpoint URL all services POST to with grant_type=client_credentials."
  value       = local.service_token_endpoint_url
}
