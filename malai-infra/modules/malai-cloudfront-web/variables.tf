variable "application" {
  description = "Application name. This will be added as a prefix to all resources"
}

variable "env" {
  description = "The environment name e.g. dev, prod. This will be added as a prefix to all resources"
}

variable "region" {
  description = "The AWS region"
}

variable "app_ffco" {
  description = "The name of the FFCO application"
}

variable "app_identifier" {
  description = "The identifier of the web application which is used in resource names, ids, etc. E.g: malai-webapp, malai-website."
}

variable "app_name_tag" {
  description = "Application name used in tags."
}

variable "rewrite_uri_function" {
  description = "The request uri rewrite function (CloudFront function source)."
}

variable "app_domain" {
  description = "The website domain (CloudFront alias)."
}

variable "root_public_domain" {
  description = "Public domain — Route 53 hosted zone for ACM validation."
}

variable "cloudfront_cache_ttl_seconds" {
  description = "Cache TTL in seconds (used for both default and max TTL on the cache policy)."
}

variable "s3_bucket_name_suffix" {
  default     = "static-website"
  description = "S3 bucket name suffix"
}

variable "content_security_policy" {
  description = <<-EOT
    Content-Security-Policy header value. SPAs that load inline scripts/styles
    need `'unsafe-inline'`; static marketing sites should be tighter. Pass an
    empty string to skip the CSP entirely (other security headers still apply).
  EOT
  type        = string
  default     = ""
}

variable "error_responses_for_spa" {
  description = <<-EOT
    True for client-routed SPAs: 4xx/5xx all rewrite to `200 / index.html` so
    the SPA's router can take over. False for static multi-page sites: only
    map S3's 403-on-missing-key into a regular 404 (and shape any HTTP 404
    onto `/404.html`); other status codes flow through unchanged so real
    server / origin issues stay visible.
  EOT
  type        = bool
  default     = true
}

variable "noncurrent_version_days" {
  description = <<-EOT
    Days to keep non-current S3 object versions before lifecycle expiry.
    Versioning lets us roll back a bad deploy; this bounds the storage cost.
    90 days covers any realistic rollback window.
  EOT
  type        = number
  default     = 90
}

variable "enable_www_alias" {
  description = <<-EOT
    Adds `www.<app_domain>` as a certificate SAN, a distribution alias and a
    Route 53 alias record, so the www hostname resolves and terminates TLS on
    this distribution. The redirect to the apex must live in the caller's
    `rewrite_uri_function` — CloudFront allows a single viewer-request
    function per behaviour, so the module cannot inject one. Only sensible
    when `app_domain` is a zone apex (the website); leave off for subdomain
    apps like the webapp, where `www.app.<domain>` would be noise.
  EOT
  type        = bool
  default     = false
}

variable "web_acl_id" {
  description = <<-EOT
    ARN of a CLOUDFRONT-scope WAFv2 WebACL to attach to the distribution.
    Empty string skips the attachment. The WAF resource itself is owned by
    the consuming infra-unit (so its rules can vary per app); the module just
    threads the ARN through. Despite the field being named `web_acl_id`,
    CloudFront expects the ARN — that's a legacy naming quirk of the AWS
    provider.
  EOT
  type        = string
  default     = ""
}

variable "alarms_sns_topic_arn" {
  description = <<-EOT
    SNS topic ARN that receives CloudWatch alarm state changes for this
    distribution (5xx error rate). Empty string disables alarms entirely.
    Typically wired to the malai-api shared alerts topic
    (`mal-<env>-malai-api-alerts`) so static-site failures land in the same
    inbox as backend failures.
  EOT
  type        = string
  default     = ""
}

variable "cloudfront_5xx_threshold_percent" {
  description = <<-EOT
    Threshold (percent) for the CloudFront 5xxErrorRate alarm. Sustained
    above this for two consecutive 5-minute periods → page. CloudFront
    quotes the metric as a percentage of total requests, so 1 = 1%.
  EOT
  type        = number
  default     = 1
}
