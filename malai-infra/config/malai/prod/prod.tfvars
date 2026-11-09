application = "mal"

env = "prod"

region = "us-east-1"

app_mal           = "mal"
app_malai_api     = "malai-api"
app_malai_webapp  = "malai-webapp"
app_malai_website = "malai-website"

root_public_domain   = "malaibank.com"
malai_api_domain     = "api.app.malaibank.com"
malai_webapp_domain  = "app.malaibank.com"
malai_website_domain = "malaibank.com"

malai_api_ses_domain_identity        = "malaibank.com"
malai_api_cognito_from_email_address = "Malai Bank <donotreply@malaibank.com>"

vpc_cidr_block = "10.0.0.0/16"

aws_azs = [
  "us-east-1a",
  "us-east-1b",
  "us-east-1c",
]

public_subnets = [
  "10.0.41.0/24",
  "10.0.45.0/24",
  "10.0.49.0/24",
]

private_subnets = [
  "10.0.52.0/24",
  "10.0.56.0/24",
  "10.0.60.0/24",
]

#   use "0.0.0.0/0" to enable incoming traffic for all
#   use "89.45.678.123/32" to enable incoming traffic for one IP
external_access_allowed_ip_addresses = [
  "203.0.113.10/32",
]

malai_api_max_concurrency = 50
malai_api_max_size        = 2
malai_api_min_size        = 1
malai_api_instance_cpu    = "1 vCPU"
malai_api_instance_memory = "2 GB"

# Off in prod, for two reasons that happen to agree.
#
# Cost: this is the only log line in the stack whose volume is set by traffic
# rather than by us. At demo scale it is pennies, but a bot that finds the API
# turns full request + response bodies into CloudWatch ingestion at $0.50/GB
# with no ceiling. Everything else in this environment bills flat.
#
# Credibility: a bank evaluator reading the config would flag full
# request/response body capture in a production banking environment — those
# bodies carry account numbers, balances and payee details. Dev keeps it on
# because dev's data is seeded and the debugging value is real.
malai_api_gw_log_full_request_response_body = false

# Prod-demo runs on Spot. ~$29/mo -> ~$9/mo for the always-on task.
#
# The trade, stated plainly: AWS can reclaim the task on 2 minutes' notice,
# and at min_size = 1 there is no second task to cover the gap — a reclaim is
# ~60-90s of 503s, unscheduled. If a bank exec clicking the demo link during
# that window is unacceptable, set this false and pay the ~$20/mo.
malai_api_use_spot = true

# Prod must alarm on API Gateway 5xx — unlike dev, nothing here is
# intentionally asleep, so a 5xx is always worth knowing about.
enable_request_alarms = true

malai_webapp_cache_ttl_seconds  = 120
malai_website_cache_ttl_seconds = 120

# Off as a cost decision, not a teardown leftover. These are six TLS-expiry
# alarms ($0.60/mo) firing into `mal-prod-malai-api-alerts`, and prod sets no
# `alert_emails`, so the topic has no subscribers and nobody would be paged.
# ACM renews DNS-validated certs on its own, so the alarms are belt-and-braces
# rather than the only thing standing between us and an expired cert.
#
# They were switched off in the 2026-09-02 teardown because the topic they
# reference went with ecs-malai-api; the 2026-09-17 restore brought the topic
# back but left these false. Flip both to true in the same change that gives
# the topic a subscriber.
malai_website_alarms_enabled = false
malai_webapp_alarms_enabled  = false

# Left at its default (true): both CMKs are back since the 2026-09-17 restore
# and every backend log group is encrypted with the logs key. The flag exists
# for the torn-down state, where the logs key has no consumer.
# enable_logs_kms_key = false

# Bank-level identifiers used by AccountIdentifierFactory to generate SORT_CODE + IBAN_BIC
# records on every account creation. Sort code is the dashed UK form; first 4 chars of the BIC
# become the IBAN bank code.
#
# Plan 02-09 reserved `65-82` as the Malai prefix (verified unallocated against Pay.UK /
# VALACDOS on 2026-05-06; canonical sort code `65-82-37`). Malai is a demo bank — there is
# no real Pay.UK assignment in flight, so the canonical value is used in prod too.
# Plan 02-12's AccountNumberGeneratorConfiguration fails fast at boot if this value is
# outside the prefix.
bank_sort_code = "65-82-37"
bank_bic       = "MALAGB22XXX"

cors_allowed_origins = ["https://app.malaibank.com"]

# Payment screening runs in enforcing mode. Malai's "prod" is the polished demo a bank
# evaluator explores unattended, so it needs the same headline loop as dev: a flagged
# payment parks in REFERRED and only a human ruling moves it. See dev.tfvars for the
# deterministic trigger amounts.
screening_mode = "ENFORCE"

# WAF stays off in prod, as a cost decision rather than a pause artefact.
#
# Cost if enabled: ~$5/mo per WebACL + $1/mo per managed rule group +
# $0.60/M evaluated requests. Three managed rule groups (Common Rule Set,
# Known Bad Inputs, IP Reputation) + one custom rate-based rule at
# 2000 req/5min/IP, across two distributions (api-gw, webapp) ~= $16/mo at
# zero-traffic baseline. Per D-10.
#
# The reasoning: WAF is invisible in a demo. Nothing a bank evaluator clicks
# through reveals whether it is on. What they may ask is "do you have a WAF?",
# and the answer is the Terraform in api-gateway-malai-api/waf.tf plus this
# one flag — which is a better answer than a $16/mo bill on an environment
# with no real traffic to protect.
#
# Flip to true for a specific demo where WAF behaviour is the subject, or
# permanently if this environment ever carries traffic worth filtering. If
# cost still matters then, attaching only to the api-gateway distribution and
# leaving the marketing SPA unprotected halves it to ~$8/mo.
enable_waf = false

# Prod pins its own RDS sizing rather than inheriting the module defaults.
#
# These values match what the defaults happen to be today (db.t4g.micro,
# single-AZ). They are written out because prod silently tracking a default is
# how the README ended up claiming a Multi-AZ db.t4g.small that was never
# deployed. Multi-AZ roughly doubles the instance cost (~$12 -> ~$24/mo) and
# buys failover this environment has no need for — a demo bank that is briefly
# down is a demo bank that is briefly down.
rds_instance_class = "db.t4g.micro"
rds_multi_az       = false

# Private-subnet egress via a t4g.nano NAT instance (~$7/mo) rather than the
# managed NAT Gateway (~$33/mo). Keeps ECS and RDS in private subnets, so the
# architecture still reads correctly to an evaluator, at a fifth of the cost.
# Flip to "gateway" if egress ever needs to survive an AZ failure.
nat_mode = "instance"
