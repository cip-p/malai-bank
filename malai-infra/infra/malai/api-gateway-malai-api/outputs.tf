output "api_id" {
  value       = aws_apigatewayv2_api.this.id
  description = "HTTP API v2 ID."
}

output "api_endpoint" {
  value       = aws_apigatewayv2_api.this.api_endpoint
  description = "Default execute-api endpoint (https://{id}.execute-api.{region}.amazonaws.com)."
}

output "stage_arn" {
  value       = aws_apigatewayv2_stage.this.arn
  description = "Stage ARN — referenced by WAF associations and CloudWatch alarms."
}

output "vpc_link_id" {
  value = aws_apigatewayv2_vpc_link.this.id
}
