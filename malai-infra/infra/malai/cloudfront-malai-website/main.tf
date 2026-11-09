# Reuse the SNS topic created by ecs-malai-api/alarms.tf so static-site
# failures land in the same inbox as backend failures.
data "aws_sns_topic" "alerts" {
  count = var.malai_website_alarms_enabled ? 1 : 0
  name  = "${var.application}-${var.env}-malai-api-alerts"
}

module "malai-website" {
  source = "../../../modules/malai-cloudfront-web"

  application                  = var.application
  env                          = var.env
  region                       = var.region
  app_ffco                     = var.app_mal
  app_name_tag                 = var.app_malai_website
  app_identifier               = "malai-website"
  rewrite_uri_function         = file("${path.module}/functions/rewrite_uri.js")
  root_public_domain           = var.root_public_domain
  app_domain                   = var.malai_website_domain
  cloudfront_cache_ttl_seconds = var.malai_website_cache_ttl_seconds
  enable_www_alias             = true

  # Astro multi-page static site — let real 4xx/5xx flow through. Module
  # only translates S3's 403-on-missing-key into a regular 404 so users get
  # the proper not-found page.
  error_responses_for_spa = false

  # No WAF on the marketing site (no auth flows, no high-value endpoints to
  # attack). Re-evaluate if it ever hosts forms.

  # 5xx alarm wired to the shared malai-api alerts topic.
  alarms_sns_topic_arn = var.malai_website_alarms_enabled ? data.aws_sns_topic.alerts[0].arn : ""

  # Tighter CSP for the static marketing site — no inline JS, no third-party
  # scripts unless explicitly listed. If the site grows analytics or fonts,
  # add the relevant origins to `script-src` / `style-src` / `font-src`.
  content_security_policy = join(" ", [
    "default-src 'self';",
    "script-src 'self' https://plausible.io;",
    "style-src 'self' 'unsafe-inline' fonts.googleapis.com;",
    "font-src 'self' fonts.gstatic.com data:;",
    "img-src 'self' data: https:;",
    "connect-src 'self' https://plausible.io;",
    "frame-ancestors 'none';",
    "base-uri 'self';",
    "form-action 'self';",
  ])
}
