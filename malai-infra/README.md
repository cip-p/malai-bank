# malai-infra

Infrastructure as code for Malai, written in Terraform for AWS. It runs the three public faces of
the bank: the marketing website, the customer webapp, and the `malai-api` backend with its
database, identity provider and simulated payment schemes.

There are two environments, `dev` and `prod`, each in its own AWS account. Everything lives in
`us-east-1`, the region CloudFront requires for its TLS certificates.

## What it deploys

```
                         Route 53
          ┌─────────────────┼──────────────────────┐
     CloudFront         CloudFront             CloudFront
     website (S3)       webapp (S3)            api.*
                                                  │
                                       API Gateway (HTTP API)
                                                  │ VPC link + Cloud Map
                     ┌────────────── private subnets ──────────────┐
                     │   ECS Fargate: malai-api ──── malai-stub    │
                     │                   │                         │
                     │          RDS Postgres 17                    │
                     └──────────── egress via NAT ─────────────────┘

      Cognito (passwordless sign-in, emails via SES) · KMS · Secrets Manager · CloudWatch alarms
```

- **Static sites.** The website and webapp are S3 buckets behind CloudFront with origin access
  control, built from a shared module.
- **API.** API Gateway reaches the ECS service through a VPC link and Cloud Map service
  discovery, so there is no load balancer. A separate CloudFront distribution fronts it on the
  `api.*` domain.
- **Compute.** `malai-api` runs on Fargate, optionally on Spot. `malai-stub`, the simulated
  outside world (payment schemes and Confirmation of Payee), runs next to it. The seeder is a
  task definition that runs once when you reset the demo data; it has no long-running service.
- **Data.** RDS Postgres sits in private subnets, and RDS keeps its master password in Secrets
  Manager. Two KMS keys encrypt the secrets and the logs.
- **Identity.** A Cognito user pool handles passwordless email sign-in, with SES sending the
  emails. A second app client issues the service tokens the backend uses for internal calls.
- **Images.** Container images come from an ECR registry in a separate, shared AWS account,
  which this repo doesn't manage.

## Layout

| Path | Contents |
|---|---|
| `config/malai/<env>/<env>.tfvars` | The environment's values: domains, sizing, feature flags. |
| `config/malai/<env>/backend-<env>.tfvars` | Where state lives (S3 bucket, with lockfile) and which IAM role to assume. |
| `infra/malai/<unit>/` | One Terraform root per concern, each with its own state file. The `dep` file lists the units it depends on. |
| `modules/malai-cloudfront-web/` | The S3 + CloudFront static-site module used by both web units. |

### Units

| Unit | What it creates | Depends on |
|---|---|---|
| `init` | Nothing; a placeholder the other units list first | none |
| `dns` | The Route 53 hosted zone | none |
| `ses-malai-api` | The SES domain identity and DKIM keys | none |
| `kms` | KMS keys for secrets and logs | `init` |
| `vpc` | VPC, public and private subnets, NAT (instance or gateway) | `init` |
| `cognito-malai-api` | User pool, app clients, resource server, custom-message Lambda, client secrets | `ses-malai-api`, `kms` |
| `rds` | Postgres instance, subnet and parameter groups | `vpc`, `kms` |
| `ecs-malai-api` | ECS cluster and service, task definition, Cloud Map namespace, alarms and their SNS topic | `vpc`, `rds`, `cognito-malai-api`, `kms` |
| `ecs-malai-seeder` | The seeder's one-off task definition and IAM roles | `ecs-malai-api` and its dependencies |
| `ecs-malai-stub` | `malai-stub` service and its Cloud Map entry | `ecs-malai-api`, `ecs-malai-seeder` |
| `api-gateway-malai-api` | HTTP API, VPC link, the `api.*` CloudFront distribution, optional WAF | `ecs-malai-api`, `cognito-malai-api`, `dns` |
| `cloudfront-malai-webapp` | Webapp bucket and distribution, optional WAF | `dns` |
| `cloudfront-malai-website` | Website bucket and distribution | `dns` |

## Running Terraform

You need Terraform **1.14.9** exactly, since every unit pins `required_version`. With Homebrew:

```bash
brew install warrensbox/tap/tfswitch
tfswitch 1.14.9
```

You also need AWS credentials that can assume the role named in `backend-<env>.tfvars`.

Run every command from inside a unit directory. The backend file is passed twice: once to
configure the state backend, and once as variables, because the provider reads the role to
assume from it.

```bash
cd infra/malai/vpc
terraform init -reconfigure -backend-config=../../../config/malai/dev/backend-dev.tfvars
terraform plan  -var-file=../../../config/malai/dev/backend-dev.tfvars -var-file=../../../config/malai/dev/dev.tfvars
terraform apply -var-file=../../../config/malai/dev/backend-dev.tfvars -var-file=../../../config/malai/dev/dev.tfvars
```

**Always pass `-reconfigure`.** Dev and prod share the same unit directories. Without it, an
`init` against the other environment's backend offers to migrate state, and accepting copies one
environment's state over the other's.

### Apply order on a fresh environment

Following the `dep` files:

1. `init`, `dns`, `ses-malai-api`. Then publish the three SES DKIM records in the domain's DNS
   (SES console → the identity → *Authentication* → *Publish DNS records*). `cognito-malai-api`
   fails until the domain is verified.
2. `kms`, `vpc`
3. `cognito-malai-api`, `rds`
4. `ecs-malai-api`
5. `ecs-malai-seeder`, then `ecs-malai-stub`
6. `api-gateway-malai-api`

`cloudfront-malai-webapp` and `cloudfront-malai-website` only need `dns`, so they can go at any
point after step 1.

### Re-creating after a destroy

`terraform destroy` leaves every Secrets Manager secret in its 30-day recovery window, and a new
secret with the same name fails with *"already scheduled for deletion"*. If the old values aren't
worth recovering, force-delete them before re-applying:

```bash
aws secretsmanager list-secrets --include-planned-deletion --query 'SecretList[].Name'
aws secretsmanager delete-secret --secret-id <name> --force-delete-without-recovery
```

## Cost and resilience switches

The environments are sized for demo traffic. Each of the cheaper choices is a variable you can
flip:

| Variable | Cheap setting | Alternative |
|---|---|---|
| `nat_mode` | `"instance"`: a single t4g.nano NAT instance | `"gateway"`: managed NAT Gateway, survives an AZ failure |
| `malai_api_use_spot` | `true`: Fargate Spot | `false`: on-demand Fargate, no 2-minute reclaim gaps |
| `enable_waf` | `false` | `true`: managed rule groups and a rate limit |
| `rds_multi_az` | `false` | `true`: standby replica, roughly doubles the RDS cost |

Some savings were considered and deliberately left out:

- **VPC interface endpoints instead of NAT.** The workload needs six of them, which would cost
  more than the NAT Gateway they replace.
- **Fargate tasks in public subnets.** That removes NAT entirely, but puts the application tier
  in a public subnet.
- **Scheduled shutdown of prod.** A demo link that's dead out of hours costs more than it saves.

## CI

`.github/workflows/malai-infra.yml` checks formatting and validates every unit without a backend
or AWS credentials. It never runs `plan` or `apply`. To run the same checks locally from this
directory:

```bash
terraform fmt -recursive -check -diff
find . -name '*.tf' -not -path '*/.terraform/*' -exec dirname {} \; | sort -u | while read -r unit; do
  (cd "$unit" && terraform init -input=false -backend=false >/dev/null && terraform validate) || echo "FAILED: $unit"
done
```
