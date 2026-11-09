locals {
  name_prefix = "${var.application}-${var.env}"

  # Project + Environment tags let downstream modules discover the VPC via `data "aws_vpc"`.
  # `zone` tag on subnets lets them be filtered by role (public/private).
  tags = {
    Environment = var.env
    Project     = var.application
    Terraform   = true
    infra-unit  = "vpc"
    Apps        = var.app_mal
  }

  # `enable_nat` is the on/off switch (deep-sleep flips it); `nat_mode` picks the
  # implementation. Both must agree for either resource set to exist, so
  # `enable_nat = false` still destroys everything regardless of mode — that's
  # what bin/sleep_deep.sh relies on.
  nat_gateway_enabled  = var.enable_nat && var.nat_mode == "gateway"
  nat_instance_enabled = var.enable_nat && var.nat_mode == "instance"

  nat_routes = concat(
    [for id in aws_nat_gateway.this[*].id : {
      nat_gateway_id       = id
      network_interface_id = null
    }],
    [for eni in aws_instance.nat[*].primary_network_interface_id : {
      nat_gateway_id       = null
      network_interface_id = eni
    }],
  )
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr_block
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-vpc"
  })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-igw"
  })
}

# 3 public subnets, one per AZ. Host the NAT gateway and (future) public-facing load balancers.
resource "aws_subnet" "public" {
  count = length(var.aws_azs)

  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnets[count.index]
  availability_zone       = var.aws_azs[count.index]
  map_public_ip_on_launch = false

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-public-${var.aws_azs[count.index]}"
    zone = "public"
  })
}

# 3 private subnets, one per AZ. Host ECS tasks, RDS, internal NLB.
resource "aws_subnet" "private" {
  count = length(var.aws_azs)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnets[count.index]
  availability_zone = var.aws_azs[count.index]

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-private-${var.aws_azs[count.index]}"
    zone = "private"
  })
}

# Single NAT gateway for all private subnets. Cheaper than per-AZ.
# Multi-AZ upgrade path: add `count = length(var.aws_azs)` here and in the EIP,
# then split the private route table into one-per-AZ pointing at the matching NAT.
#
# `count = var.enable_nat ? 1 : 0` lets dev "deep sleep" destroy the NAT + EIP
# (~$33/mo always-on combined). When the count is 0 the resources are gone
# entirely; the private-subnet route to NAT (further down) also disappears,
# leaving private-subnet workloads with no outbound internet — fine when ECS
# / RDS are also asleep.
resource "aws_eip" "nat" {
  count = local.nat_gateway_enabled ? 1 : 0

  domain = "vpc"

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-nat-eip"
  })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_nat_gateway" "this" {
  count = local.nat_gateway_enabled ? 1 : 0

  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-nat"
  })

  depends_on = [aws_internet_gateway.this]
}

#
# NAT instance (`nat_mode = "instance"`, the default) — a t4g.nano doing
# iptables MASQUERADE in place of the managed NAT Gateway.
#
# Malai is a demo bank, not a bank. The managed gateway bills ~$33/mo flat for
# egress that is, in practice, ECR image pulls plus Cognito/SES API calls. The
# instance does the same job for ~$7/mo all-in ($3.07 compute + $0.64 EBS +
# $3.65 for the public IPv4), and — the reason we picked it over simply moving
# the tasks into public subnets — it keeps ECS and RDS in private subnets, so
# the architecture still reads correctly to a bank evaluator walking the diagram.
#
# What you give up versus the managed gateway:
#   - It is one instance in one AZ. No automatic failover; if it dies, private
#     egress stops until EC2's default automatic recovery replaces it (a few
#     minutes). The gateway is AZ-redundant within its AZ and never needs this.
#   - It is an EC2 instance, so it is yours to patch. AL2023 handles its own
#     security updates, but the box shows up in any inventory scan.
#   - Throughput is bounded by t4g.nano network performance (burst up to ~5
#     Gbps, sustained far lower) rather than the gateway's 45 Gbps. Irrelevant
#     for image pulls and API calls; would not be for bulk data egress.
#
# Flip `nat_mode = "gateway"` to go back to the managed gateway with no other
# changes — the private route table follows whichever one is active.
#
data "aws_ami" "nat_instance" {
  count = local.nat_instance_enabled ? 1 : 0

  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-arm64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# The NAT instance forwards for the whole VPC, so it accepts anything sourced
# from inside the VPC CIDR and nothing from outside it. It is not a bastion —
# there is no SSH ingress; operator access is via SSM Session Manager, which
# dials out from the instance and needs no inbound rule.
resource "aws_security_group" "nat_instance" {
  count = local.nat_instance_enabled ? 1 : 0

  name        = "${local.name_prefix}-nat-instance"
  description = "NAT instance for private-subnet egress"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-nat-instance"
  })
}

resource "aws_security_group_rule" "nat_instance_ingress_from_vpc" {
  count = local.nat_instance_enabled ? 1 : 0

  type              = "ingress"
  security_group_id = aws_security_group.nat_instance[0].id
  protocol          = "-1"
  from_port         = 0
  to_port           = 0
  cidr_blocks       = [var.vpc_cidr_block]
  description       = "Forwarded traffic from private subnets"
}

resource "aws_security_group_rule" "nat_instance_egress" {
  count = local.nat_instance_enabled ? 1 : 0

  type              = "egress"
  security_group_id = aws_security_group.nat_instance[0].id
  protocol          = "-1"
  from_port         = 0
  to_port           = 0
  cidr_blocks       = ["0.0.0.0/0"]
  description       = "Masqueraded egress to the internet"
}

# SSM Session Manager only — no EC2 key pair, no SSH. This is what makes the
# instance debuggable ("is masquerading actually working?") without opening
# port 22 or managing a key.
resource "aws_iam_role" "nat_instance" {
  count = local.nat_instance_enabled ? 1 : 0

  name = "${local.name_prefix}-nat-instance"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "nat_instance_ssm" {
  count = local.nat_instance_enabled ? 1 : 0

  role       = aws_iam_role.nat_instance[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "nat_instance" {
  count = local.nat_instance_enabled ? 1 : 0

  name = "${local.name_prefix}-nat-instance"
  role = aws_iam_role.nat_instance[0].name

  tags = local.tags
}

resource "aws_instance" "nat" {
  count = local.nat_instance_enabled ? 1 : 0

  ami                         = data.aws_ami.nat_instance[0].id
  instance_type               = var.nat_instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.nat_instance[0].id]
  iam_instance_profile        = aws_iam_instance_profile.nat_instance[0].name
  associate_public_ip_address = true

  # The whole point: without this, EC2 drops packets whose source/destination
  # is not the instance itself, which is every packet a NAT forwards.
  source_dest_check = false

  metadata_options {
    http_tokens   = "required"
    http_endpoint = "enabled"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 8
    encrypted   = true
  }

  # Terraform interpolates `${...}`, so the script below uses `$(...)` and
  # unbraced `$VAR` throughout. The only Terraform-interpolated value is the
  # VPC CIDR in the MASQUERADE rule.
  user_data = <<-BASH
    #!/bin/bash
    set -euxo pipefail

    fallocate -l 1G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap defaults 0 0' >>/etc/fstab

    dnf install -y iptables-services

    cat >/etc/sysctl.d/99-nat.conf <<'SYSCTL'
    net.ipv4.ip_forward = 1
    SYSCTL
    sysctl --system

    PRIMARY_IF="$(ip -o -4 route show to default | awk '{print $5}' | head -n1)"
    iptables -t nat -A POSTROUTING -o "$PRIMARY_IF" -s ${var.vpc_cidr_block} -j MASQUERADE
    iptables -P FORWARD ACCEPT

    systemctl enable iptables
    service iptables save
  BASH

  # Rebuild the instance when the script changes — the rules are applied at
  # first boot, so an in-place update would leave a running instance on the
  # old configuration with no signal that it had drifted.
  user_data_replace_on_change = true

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-nat-instance"
  })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-public-rt"
  })
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id

  # The 0.0.0.0/0 → NAT route is a `dynamic` block that vanishes when
  # `var.enable_nat = false` (deep-sleep mode) — same lifecycle as the NAT
  # itself. Using a dynamic inline block (rather than a separate `aws_route`
  # resource) means terraform manages all routes for this route table in
  # one place; mixing inline + standalone routes on the same table makes
  # them fight each other.
  #
  # `local.nat_routes` is built from splat expressions rather than
  # `local.nat_*_enabled ? aws_x[0] : null` because a `[0]` index against a
  # zero-count resource is an error at plan time, whereas `[*]` on the same
  # resource is simply an empty list. Exactly one of the two lists is
  # non-empty, so this yields at most one default route.
  dynamic "route" {
    for_each = local.nat_routes
    content {
      cidr_block           = "0.0.0.0/0"
      nat_gateway_id       = route.value.nat_gateway_id
      network_interface_id = route.value.network_interface_id
    }
  }

  tags = merge(local.tags, {
    Name = "${local.name_prefix}-private-rt"
  })
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
