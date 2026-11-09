output "vpc_id" {
  value = aws_vpc.this.id
}

output "vpc_cidr_block" {
  value = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "nat_instance_id" {
  value       = one(aws_instance.nat[*].id)
  description = "NAT instance ID, or null when nat_mode = \"gateway\" or NAT is disabled. Use with `aws ssm start-session` to debug egress."
}

output "nat_public_ip" {
  value       = coalesce(one(aws_eip.nat[*].public_ip), one(aws_instance.nat[*].public_ip), "none")
  description = "Current egress public IP. Changes on every deep-sleep cycle in both NAT modes — not stable enough to allow-list."
}
