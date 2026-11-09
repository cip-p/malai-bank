output "task_definition_family" {
  value       = try(aws_ecs_task_definition.this[0].family, null)
  description = "ECS task-definition family for the seeder. Null in non-dev environments where count=0."
}

output "task_security_group_id" {
  value       = try(aws_security_group.task[0].id, null)
  description = "Security group attached to the seeder Fargate task. Consumed by malai-backend's ./demo-reset.sh."
}

output "cluster_name" {
  value       = try(data.aws_ecs_cluster.malai_api[0].cluster_name, null)
  description = "ECS cluster the seeder task runs against (reuses the malai-api cluster). Null in non-dev environments."
}
