output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecs_service_name" {
  value = aws_ecs_service.this.name
}

output "task_definition_family" {
  value = aws_ecs_task_definition.this.family
}

output "service_discovery_service_arn" {
  value       = aws_service_discovery_service.malai_api.arn
  description = "Cloud Map service ARN for the API Gateway HTTP API v2 integration_uri."
}

output "service_discovery_dns_name" {
  value       = "${aws_service_discovery_service.malai_api.name}.${aws_service_discovery_private_dns_namespace.this.name}"
  description = "In-VPC DNS name for the malai-api tasks. SRV records — resolve with `dig SRV`, not `dig A`."
}

output "task_security_group_id" {
  value       = aws_security_group.task.id
  description = "Task SG — api-gateway-malai-api references this so the VPC Link SG can be granted ingress to the container port."
}
