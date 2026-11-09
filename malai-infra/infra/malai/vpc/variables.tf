variable "application" {
  description = "Application name. This will be added as a prefix to all resources"
}

variable "app_mal" {
  description = "The name of the Malai application"
}

variable "env" {
  description = "The environment name e.g. dev, prod. This will be added as a prefix to all resources"
}

variable "assume_role" {
  description = "Role to run infra-unit as. Shape matches the S3 backend `assume_role` block."
  type = object({
    role_arn = string
  })
}

variable "region" {
  description = "The AWS region"
}

variable "vpc_cidr_block" {
  description = "CIDR block for the VPC"
}

variable "aws_azs" {
  description = "Availability zones to distribute subnets across. All 3 AZs get subnets so we can scale to multi-AZ without reshaping the network."
  type        = list(string)
}

variable "public_subnets" {
  description = "CIDR blocks for the public subnets, one per AZ in aws_azs"
  type        = list(string)
}

variable "private_subnets" {
  description = "CIDR blocks for the private subnets, one per AZ in aws_azs"
  type        = list(string)
}

variable "nat_mode" {
  description = <<-EOT
    Which NAT implementation provides private-subnet egress. Only consulted
    when `enable_nat = true`.

      instance  A t4g.nano running iptables MASQUERADE (~$7/mo all-in).
                The default: Malai is a demo bank and the managed gateway's
                ~$33/mo buys AZ-redundancy and 45 Gbps we have no use for.
                Single AZ, single instance, yours to patch.

      gateway   The managed AWS NAT Gateway (~$33/mo). Pick this if egress
                ever needs to be genuinely highly available, or if the
                instance turns into an operational nuisance.

    Both modes are transparent to everything downstream — the private route
    table points at whichever one is active.
  EOT
  type        = string
  default     = "instance"

  validation {
    condition     = contains(["instance", "gateway"], var.nat_mode)
    error_message = "nat_mode must be either \"instance\" or \"gateway\"."
  }
}

variable "nat_instance_type" {
  description = <<-EOT
    Instance type for the NAT instance (`nat_mode = "instance"`). Must be an
    ARM64 type — the AMI filter selects an arm64 AL2023 image.
  EOT
  type        = string
  default     = "t4g.nano"
}

variable "enable_nat" {
  description = <<-EOT
    Whether to provision private-subnet egress (NAT instance or NAT Gateway,
    per `nat_mode`) and route private subnets through it. Default true.

    Set false in dev "deep sleep" mode (no developer working on the app for
    several days) to drop the always-on NAT cost (~$7/mo on the default
    instance mode, ~$33/mo on gateway mode). While disabled, private-subnet
    workloads have no outbound internet path — which is fine when ECS / RDS
    are also stopped via bin/sleep.sh.

    The egress public IP changes on every wake in both modes — the gateway's
    EIP is destroyed with it, and the NAT instance gets a fresh auto-assigned
    public IP. Do not rely on a stable egress IP for any allow-listing.

    Wake order matters: re-enable NAT (apply this var=true) BEFORE running
    bin/wake.sh, otherwise the ECS task can't pull the OTel collector image
    from public.ecr.aws.

    Prod must keep this true.
  EOT
  type        = bool
  default     = true
}
