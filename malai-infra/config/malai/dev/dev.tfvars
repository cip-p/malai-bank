application = "mal"

env = "dev"

region = "us-east-1"

app_mal           = "mal"
app_malai_api     = "malai-api"
app_malai_webapp  = "malai-webapp"
app_malai_website = "malai-website"

root_public_domain   = "malaibankdev.com"
malai_api_domain     = "api.app.malaibankdev.com"
malai_webapp_domain  = "app.malaibankdev.com"
malai_website_domain = "malaibankdev.com"

malai_api_ses_domain_identity        = "malaibank.com"
malai_api_cognito_from_email_address = "Malai Bank <donotreplydev@malaibank.com>"

vpc_cidr_block = "10.0.0.0/16"

aws_azs = [
  "us-east-1a",
  "us-east-1b",
  "us-east-1c",
]

public_subnets = [
  "10.0.11.0/24",
  "10.0.15.0/24",
  "10.0.19.0/24",
]

private_subnets = [
  "10.0.20.0/24",
  "10.0.24.0/24",
  "10.0.28.0/24",
]

#   use "0.0.0.0/0" to enable incoming traffic for all
#   use "89.45.678.123/32" to enable incoming traffic for one IP
external_access_allowed_ip_addresses = [
  "203.0.113.10/32",
]

malai_api_max_concurrency = 50
malai_api_max_size        = 2
# Dev is "off by default" — woken via bin/wake.sh, put to sleep via bin/sleep.sh.
# min=0 lets manual `aws ecs update-service --desired-count 0` stick (otherwise
# the autoscaling floor would pull it back to 1). Prod must set this to ≥1.
malai_api_min_size        = 0
malai_api_instance_cpu    = "1 vCPU"
malai_api_instance_memory = "2 GB"

# Dev: don't alarm on API Gateway 5xx since the service is intentionally
# asleep most of the time. Every probe returns 503 while there are no healthy
# Cloud Map instances, which would otherwise spam alerts. Prod must set true.
# (Read by api-gateway-malai-api since the ALB was replaced by Cloud Map.)
enable_request_alarms = false

# Spot is a clear win in dev: the environment is asleep most of the time, and
# nobody is watching when a reclaim happens.
malai_api_use_spot = true

# Cost discipline: dev deep-sleep tears down WAF anyway via bin/sleep_deep.sh,
# and bin/wake_deep.sh recreates it on demand via -var=enable_waf=true CLI
# override. Setting enable_waf = false here keeps WAF off when dev is awake
# too -- acceptable for a pet project with no prod traffic. Saves ~$16/mo
# (2 WebACLs at ~$8/mo each) per D-10. Re-enable temporarily if testing
# rate limiting or rule changes against dev.
enable_waf = false

# Dev-only: skip final snapshot on destroy, apply changes without waiting for the next maintenance window.
rds_skip_final_snapshot = true
rds_apply_immediately   = true

# Same NAT instance as prod. bin/sleep_deep.sh still destroys it via
# -var=enable_nat=false; nat_mode only picks which implementation comes back.
nat_mode = "instance"

malai_api_gw_log_full_request_response_body = true

malai_webapp_cache_ttl_seconds  = 60
malai_website_cache_ttl_seconds = 60

# Dev has no alerting stack — ecs-malai-api's SNS topic was destroyed here long
# before prod was. The webapp flag catches up with the website one; applying it
# clears the four `mal-dev-malai-webapp-*` alarms that had been pointing at the
# dead topic.
malai_website_alarms_enabled = false
malai_webapp_alarms_enabled  = false

# Same reasoning as prod: the kms unit is destroyed outright, so this stays at
# its default rather than pinning a half-provisioned state.
# enable_logs_kms_key = false

# CloudWatch alarm recipients for the malai-api SNS topic.
# Each address gets a confirmation email from AWS the first time it's added —
# the subscription stays in `pending confirmation` (no delivery) until clicked.
# For prod, use a distribution list rather than a personal inbox.
alert_emails = ["alerts@example.com"]

# Attach the OpenTelemetry Java agent to the malai-api JVM. Safe to enable now
# that an image with /opt/otel/opentelemetry-javaagent.jar (post-Dockerfile
# change) has been deployed to dev.
enable_otel_agent = true

# Bank-level identifiers used by AccountIdentifierFactory to generate SORT_CODE + IBAN_BIC
# records on every account creation. Sort code is the dashed UK form; first 4 chars of the BIC
# become the IBAN bank code.
#
# Plan 02-09 reserved `65-82` as the Malai prefix (verified unallocated against Pay.UK /
# VALACDOS on 2026-05-06; canonical sort code `65-82-37` = MalaiSortCode.CANONICAL).
# Plan 02-12's AccountNumberGeneratorConfiguration fails fast at boot if this value is
# outside the prefix.
bank_sort_code = "65-82-37"
bank_bic       = "MALAGB22XXX"

cors_allowed_origins = [
  "https://app.malaibankdev.com",
  "http://localhost:5173",
]

# Payment screening runs in enforcing mode: a flagged outbound payment parks in REFERRED
# with the payer's funds still held, and moves only when an ops reviewer's ruling reaches
# the MDR gate. Without this the platform's headline loop is invisible — every payment is
# screened and recorded, but nothing is ever held for a human to rule on.
#
# Demo triggers (deterministic, from the backend's ScreeningProperties defaults):
#   - any payment >= GBP 10,000
#   - any amount to the watchlist counterparty "Carol Singh"
#   - exactly GBP 3,141.59 makes the decider time out, recorded as its own Outcome
screening_mode = "ENFORCE"
