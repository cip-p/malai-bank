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

# Owned by the kms module — encrypts Secrets Manager values across the stack
# (db_master here, cognito client elsewhere).
data "aws_kms_alias" "secrets" {
  name = "alias/${local.name_prefix}-secrets"
}
