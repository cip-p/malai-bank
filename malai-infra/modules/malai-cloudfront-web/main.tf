#
# malai-cloudfront-web
#
# Hardened S3 + CloudFront static-site module for the malai infra. Tracks the
# AWS-recommended modern setup as of 2026:
#
#   - **OAC (Origin Access Control)** instead of legacy OAI. SigV4-signed,
#     supports SSE-KMS buckets, and it's the API AWS continues to invest in.
#   - **TLS 1.2_2021** minimum + **HTTP/2 + HTTP/3 (QUIC)** on the viewer side.
#     Matches what we already enforce on api-gateway-malai-api.
#   - **PriceClass_100** (NA + EU edges only). Overhead-of-APAC/SA edges isn't
#     justified for UK-targeted traffic; api-gateway-malai-api is already 100.
#   - **S3 hardening**: BucketOwnerEnforced (ACLs disabled), explicit SSE-S3
#     default, versioning enabled (one-click rollback), lifecycle that purges
#     incomplete multipart uploads + ages out non-current versions.
#   - **SPA-vs-static-site error mapping**: `error_responses_for_spa = true`
#     rewrites 4xx/5xx to `200 / index.html` (correct for client-routed SPAs).
#     False keeps real 4xx/5xx (correct for multi-page static sites — exposes
#     real 404s instead of silently mapping them to the home page).
#
# Forked from `modules/cloudfront-web` so changes here don't leak into other
# projects that still use the shared module.
#

locals {
  name_prefix  = "${var.application}-${var.env}"
  s3_origin_id = "${var.application}-${var.env}-${var.app_identifier}-web-origin"
  tags = {
    Environment = var.env
    Terraform   = true
    infra-unit  = "cloudfront-web"
    Apps        = join(" ", [var.app_ffco, var.app_name_tag])
  }
}

#
# S3 origin bucket.
#
# `BucketOwnerEnforced` disables ACLs entirely (the AWS-recommended setting
# since 2023). Object access is governed exclusively by bucket / IAM policy,
# which keeps the trust boundary in one place.
#
resource "aws_s3_bucket" "this" {
  bucket = "${local.name_prefix}-${var.app_identifier}-${var.s3_bucket_name_suffix}"
  tags   = local.tags
}

resource "aws_s3_bucket_ownership_controls" "website" {
  bucket = aws_s3_bucket.this.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "website" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Explicit default-encryption declaration. AWS auto-applies SSE-S3 since
# January 2023, so this is largely belt-and-braces; declaring it makes the
# intent visible in code review and prevents silent drift if AWS ever changes
# the implicit default (or if a future apply is run by an account-level
# override).
resource "aws_s3_bucket_server_side_encryption_configuration" "website" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Versioning lets us roll back a bad deploy by restoring previous versions of
# `index.html` / asset bundles, and protects against accidental object
# deletion. Combined with the lifecycle rule below to keep storage costs
# bounded.
resource "aws_s3_bucket_versioning" "website" {
  bucket = aws_s3_bucket.this.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Two cleanup rules:
#
#   1. Abort multipart uploads that hang around incomplete for more than a
#      week. Forgotten partial uploads silently accrue storage cost otherwise.
#   2. Expire non-current object versions after `var.noncurrent_version_days`
#      (default 90). Versioning is on so we keep an audit trail of recent
#      deploys; long enough to roll back, short enough to bound cost.
resource "aws_s3_bucket_lifecycle_configuration" "website" {
  bucket = aws_s3_bucket.this.id

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    filter {} # apply to all objects

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    filter {} # apply to all objects

    noncurrent_version_expiration {
      noncurrent_days = var.noncurrent_version_days
    }
  }

  depends_on = [aws_s3_bucket_versioning.website]
}

#
# Origin Access Control (OAC) — modern replacement for OAI.
#
# CloudFront signs S3 origin requests with SigV4 using this. The S3 bucket
# policy below only grants `s3:GetObject` to requests carrying that exact
# signature scoped to this distribution's ARN.
#
resource "aws_cloudfront_origin_access_control" "this" {
  name                              = "${local.name_prefix}-${var.app_identifier}-oac"
  description                       = "OAC for ${local.name_prefix} ${var.app_identifier}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

#
# Bucket policy — grants `s3:GetObject` to the CloudFront service principal,
# scoped via `aws:SourceArn` to *this* distribution. No other distribution can
# read this bucket, even within the same account.
#
data "aws_iam_policy_document" "website_s3_policy" {
  statement {
    sid       = "AllowCloudFrontServicePrincipalReadOnly"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.this.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.this.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "website" {
  bucket = aws_s3_bucket.this.id
  policy = data.aws_iam_policy_document.website_s3_policy.json
}

#
# CloudFront viewer-request function for clean URLs (`/about` → `/about.html`,
# `/foo/` → `/foo/index.html`). The function code is supplied by the caller
# so each app can tune behaviour independently.
#
resource "aws_cloudfront_function" "rewrite_uri" {
  name    = "${local.name_prefix}-${var.app_identifier}-rewrite-request"
  runtime = "cloudfront-js-1.0"
  publish = true
  code    = var.rewrite_uri_function
}

#
# Distribution.
#
# `prevent_destroy` guards against `terraform destroy` accidents. CloudFront
# distributions take ~10 minutes to recreate and have a sticky edge cache;
# losing one outside of an explicit migration is painful.
#
resource "aws_cloudfront_distribution" "this" {
  enabled             = true
  is_ipv6_enabled     = true
  http_version        = "http2and3"
  comment             = "${local.name_prefix} Cloudfront distribution for ${var.app_identifier} web"
  default_root_object = "index.html"

  # Optional CLOUDFRONT-scope WAFv2 WebACL. The argument is named `web_acl_id`
  # for legacy reasons but CloudFront expects the ARN — see variables.tf.
  # `null` means no WAF attached (suitable for the static marketing site;
  # webapp passes a real ARN since it fronts auth flows).
  web_acl_id = var.web_acl_id != "" ? var.web_acl_id : null

  aliases = concat([var.app_domain], var.enable_www_alias ? ["www.${var.app_domain}"] : [])

  origin {
    domain_name              = aws_s3_bucket.this.bucket_regional_domain_name
    origin_id                = local.s3_origin_id
    origin_access_control_id = aws_cloudfront_origin_access_control.this.id
  }

  default_cache_behavior {
    allowed_methods  = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = local.s3_origin_id

    cache_policy_id            = aws_cloudfront_cache_policy.this.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.this.id

    viewer_protocol_policy = "redirect-to-https"
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.rewrite_uri.arn
    }
  }

  # NA + EU edges only. Saves ~30% on egress vs PriceClass_All; UK / EU users
  # are still served from a nearby POP. Bump if traffic ever shifts to APAC/SA.
  price_class = "PriceClass_100"

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # Error-response mapping diverges between SPA and static-site consumers:
  #
  #   - SPA (true): every 4xx/5xx maps to 200 + index.html so the client-side
  #     router gets a chance to handle the path. Hashed-asset 404s become 200
  #     too, but that's not a realistic failure mode (assets only 404 if the
  #     deploy is broken).
  #
  #   - Static (false): map only the S3-quirk 403 (private bucket → 403 on a
  #     missing key) into a real 404, plus shape an HTTP 404 to the bucket's
  #     `/404.html`. Real 4xx/5xx flow through unchanged so issues are visible
  #     instead of being silently rewritten to the home page.
  #
  dynamic "custom_error_response" {
    for_each = var.error_responses_for_spa ? [
      { error_code = 400, response_code = 200, response_page_path = "/index.html" },
      { error_code = 403, response_code = 200, response_page_path = "/index.html" },
      { error_code = 404, response_code = 200, response_page_path = "/index.html" },
      { error_code = 500, response_code = 200, response_page_path = "/index.html" },
      { error_code = 502, response_code = 200, response_page_path = "/index.html" },
      ] : [
      # S3 returns 403 for missing keys when the bucket is private — translate
      # that into a regular 404 so users see the proper not-found page.
      { error_code = 403, response_code = 404, response_page_path = "/404.html" },
      { error_code = 404, response_code = 404, response_page_path = "/404.html" },
    ]
    content {
      error_caching_min_ttl = 10
      error_code            = custom_error_response.value.error_code
      response_code         = custom_error_response.value.response_code
      response_page_path    = custom_error_response.value.response_page_path
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate.this.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = local.tags

  depends_on = [aws_acm_certificate_validation.cert]

  lifecycle {
    prevent_destroy = true
  }
}

#
# Response Headers Policy — adds standard security headers to every response.
#
# `security_headers_config` covers the well-known set: HSTS, X-Frame-Options,
# X-Content-Type-Options, Referrer-Policy, X-XSS-Protection, plus CSP. CSP
# varies per consumer (SPA vs static site) so it's threaded in via
# `var.content_security_policy`; an empty string skips the CSP block entirely
# (other security headers still apply).
#
# Verify with `curl -I https://${app_domain}/` after apply.
#
resource "aws_cloudfront_response_headers_policy" "this" {
  name    = "${local.name_prefix}-${var.app_identifier}-security-headers"
  comment = "Security headers (HSTS, frame options, CSP, ...) for ${var.app_identifier}"

  security_headers_config {
    strict_transport_security {
      access_control_max_age_sec = 31536000 # 1 year
      include_subdomains         = true
      preload                    = true
      override                   = true
    }
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
    xss_protection {
      mode_block = true
      protection = true
      override   = true
    }

    dynamic "content_security_policy" {
      for_each = var.content_security_policy == "" ? [] : [var.content_security_policy]
      content {
        content_security_policy = content_security_policy.value
        override                = true
      }
    }
  }
}

#
# Cache policy. Headers/cookies/query-strings excluded from the cache key
# because static assets don't vary on those, and any per-request work would
# kill cache hit rates. The deploy script puts file-specific Cache-Control
# headers on the S3 objects (immutable for hashed bundles, no-cache for HTML)
# to drive browser-side caching independently.
#
resource "aws_cloudfront_cache_policy" "this" {
  name    = "${local.name_prefix}-${var.app_identifier}-cloudfront-cache-policy"
  comment = "${local.name_prefix}-${var.app_identifier} Cloudfront cache policy"

  min_ttl     = 0
  default_ttl = var.cloudfront_cache_ttl_seconds
  max_ttl     = var.cloudfront_cache_ttl_seconds

  parameters_in_cache_key_and_forwarded_to_origin {
    # Required for `compress = true` on the behavior to take effect with a
    # custom cache policy: CloudFront caches one compressed variant per
    # encoding instead of forwarding the raw Accept-Encoding header.
    enable_accept_encoding_gzip   = true
    enable_accept_encoding_brotli = true

    cookies_config {
      cookie_behavior = "none"
    }
    query_strings_config {
      query_string_behavior = "none"
    }
    headers_config {
      header_behavior = "none"
    }
  }
}
