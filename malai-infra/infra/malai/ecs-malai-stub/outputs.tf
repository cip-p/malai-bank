output "stub_base_url" {
  value       = local.stub_base_url
  description = "In-VPC address of the stub. This is what MAL_STUB_BASE_URL on the malai-api task must be set to."
}

output "stub_security_group_id" {
  value = aws_security_group.task.id
}

output "service_discovery_service_arn" {
  value = aws_service_discovery_service.malai_stub.arn
}
