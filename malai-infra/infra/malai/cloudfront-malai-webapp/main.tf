# Reuse the SNS topic created by ecs-malai-api/alarms.tf so static-site
# failures land in the same inbox as backend failures. Naming is fixed:
# `${application}-${env}-malai-api-alerts`.
#
# Counted, because the topic only exists where the backend stack is deployed.
# An uncounted lookup makes this unit unplannable the moment ecs-malai-api is
# destroyed, which is what stranded the dev webapp alarms.
data "aws_sns_topic" "alerts" {
  count = var.malai_webapp_alarms_enabled ? 1 : 0
  name  = "${var.application}-${var.env}-malai-api-alerts"
}

module "malai-webapp" {
  source = "../../../modules/malai-cloudfront-web"

  application                  = var.application
  env                          = var.env
  region                       = var.region
  app_ffco                     = var.app_mal
  app_name_tag                 = var.app_malai_webapp
  app_identifier               = "malai-webapp"
  rewrite_uri_function         = file("${path.module}/functions/rewrite_uri.js")
  root_public_domain           = var.root_public_domain
  app_domain                   = var.malai_webapp_domain
  cloudfront_cache_ttl_seconds = var.malai_webapp_cache_ttl_seconds

  # React SPA — every 4xx/5xx must hand off to the client-side router via
  # index.html. Module default is true; we set it explicitly for documentation.
  error_responses_for_spa = true

  # WAFv2 sits in front of auth flows — see waf.tf for the rule list.
  # Empty string when `var.enable_waf = false` (deep-sleep mode); the
  # malai-cloudfront-web module treats empty string as "no WAF attached".
  web_acl_id = var.enable_waf ? aws_wafv2_web_acl.webapp[0].arn : ""

  # 5xx alarm wired to the shared malai-api alerts topic.
  alarms_sns_topic_arn = var.malai_webapp_alarms_enabled ? data.aws_sns_topic.alerts[0].arn : ""

  # Permissive CSP for the SPA — most modern frameworks emit inline scripts/
  # styles that need `'unsafe-inline'`/`'unsafe-eval'`. Tighten over time by
  # moving to nonce-based or hash-based CSP. `connect-src` lists every backend
  # origin the SPA talks to; extend when adding services.
  content_security_policy = join(" ", [
    "default-src 'self';",
    "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://plausible.io;",
    "style-src 'self' 'unsafe-inline' fonts.googleapis.com;",
    "font-src 'self' fonts.gstatic.com data:;",
    "img-src 'self' data: blob: https:;",
    "connect-src 'self' https://api.app.fcd8ayxqr72.com https://api.app.malaibank.com https://*.amazoncognito.com https://plausible.io;",
    "frame-ancestors 'none';",
    "base-uri 'self';",
    "form-action 'self';",
  ])
}
