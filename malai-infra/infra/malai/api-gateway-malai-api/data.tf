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

data "aws_route53_zone" "root_domain" {
  name         = var.root_public_domain
  private_zone = false
}

# Owned by ecs-malai-api — the Cloud Map service the HTTP API forwards to via
# VPC Link v2. Replaced the internal ALB lookup when the load balancer was
# removed; apply ecs-malai-api before this unit or these lookups fail.
data "aws_service_discovery_dns_namespace" "malai" {
  name = "${local.name_prefix}.malai.internal"
  type = "DNS_PRIVATE"
}

data "aws_service_discovery_service" "malai_api" {
  name         = "malai-api"
  namespace_id = data.aws_service_discovery_dns_namespace.malai.id
}

data "aws_security_group" "task" {
  name = "${local.name_prefix}-malai-api-task"
}

# Owned by cognito-malai-api — JWT authorizer validates tokens issued by this pool.
data "aws_cognito_user_pool" "malai_api" {
  user_pool_id = data.aws_cognito_user_pools.malai_api.ids[0]
}

data "aws_cognito_user_pools" "malai_api" {
  name = "${local.name_prefix}-malai-api-user-pool"
}

data "aws_cognito_user_pool_clients" "malai_api" {
  user_pool_id = data.aws_cognito_user_pool.malai_api.id
}

#
# Resolve the *user-facing* app client by name, not by position.
#
# This read `client_ids[0]` until 2026-08-10. That was correct only while the
# pool had exactly one client. `cognito-malai-api/service-jwt.tf` adds a
# machine-to-machine client per entry in `services_requiring_service_jwt`, and
# `aws_cognito_user_pool_clients` returns an unordered list — so `[0]` became a
# coin flip. In prod it landed on `mal-prod-malai-service`, which meant the JWT
# authorizer validated every user token against the service client's ID and
# rejected all of them with a bare `{"message":"Unauthorized"}` from the
# gateway. Login succeeded, then every authenticated call 401'd.
#
# Looking each client up and filtering on `name` is stable no matter how many
# service clients get added later. `one()` fails the plan loudly if the expected
# client is missing or somehow duplicated, rather than silently picking wrong.
#
data "aws_cognito_user_pool_client" "all" {
  for_each = toset(data.aws_cognito_user_pool_clients.malai_api.client_ids)

  user_pool_id = data.aws_cognito_user_pool.malai_api.id
  client_id    = each.value
}

data "aws_kms_alias" "logs" {
  name = "alias/${local.name_prefix}-logs"
}
