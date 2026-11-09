data "aws_caller_identity" "current" {}

data "aws_vpc" "primary" {
  tags = {
    Project     = var.application
    Environment = var.env
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.primary.id]
  }

  tags = {
    Project     = var.application
    Environment = var.env
    zone        = "private"
  }
}

# Owned by ecs-malai-api. The stub joins the existing cluster rather than
# standing up its own — an ECS cluster is free, but a second one would mean a
# second set of alarms, capacity providers and dashboards for one small task.
data "aws_ecs_cluster" "malai_api" {
  cluster_name = "${local.name_prefix}-malai-api"
}

# Owned by ecs-malai-api. The stub registers into the same Cloud Map namespace,
# so malai-api reaches it at malai-stub.<namespace> without a load balancer.
data "aws_service_discovery_dns_namespace" "malai" {
  name = "${local.name_prefix}.malai.internal"
  type = "DNS_PRIVATE"
}

# Owned by ecs-malai-api — the task SG the stub must accept traffic from, and
# the one it calls back into. Singular (so a missing one is an error) because
# malai-api is not optional in any environment.
data "aws_security_group" "malai_api_task" {
  name = "${local.name_prefix}-malai-api-task"
}

# One lookup per declared consumer. Deliberately the plural data source: it
# returns an empty list instead of failing when the security group is absent,
# which is what lets var.malai_stub_consumers name a unit unconditionally even
# in an environment where that unit is not deployed.
data "aws_security_groups" "consumer" {
  for_each = toset(var.malai_stub_consumers)

  filter {
    name   = "group-name"
    values = ["${local.name_prefix}-${each.value}-task"]
  }

  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.primary.id]
  }
}

data "aws_kms_alias" "logs" {
  name = "alias/${local.name_prefix}-logs"
}
