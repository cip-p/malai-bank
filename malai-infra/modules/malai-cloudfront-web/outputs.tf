output "domain_name" {
  description = "CloudFront distribution domain (e.g. d1c5qlkkgb80m2.cloudfront.net)."
  value       = aws_cloudfront_distribution.this.domain_name
}

output "distribution_id" {
  description = "CloudFront distribution ID — useful for invalidations from the deploy script."
  value       = aws_cloudfront_distribution.this.id
}

output "bucket_name" {
  description = "S3 bucket the deploy script uploads to."
  value       = aws_s3_bucket.this.id
}

output "acm_certificate_arn" {
  description = "ARN of the ACM certificate used by this CloudFront distribution. Consumers wire this into a `aws_cloudwatch_metric_alarm` to watch DaysToExpiry. All certs in this stack live in us-east-1 because CloudFront viewer-cert is a us-east-1 constraint."
  value       = aws_acm_certificate.this.arn
}
