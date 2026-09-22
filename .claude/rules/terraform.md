---
paths:
  - "iac/**/*.tf"
  - "iac/**/*.tfvars*"
---
# Terraform rules

- Pin versions: `required_version >= 1.10` and `aws ~> 6.0` in every `versions.tf`.
- No hard-coded account IDs, ARNs, regions, or AMI IDs. Use variables or data sources.
- Least privilege IAM: scope every statement to specific resource ARNs. Explain in a comment any `"*"` that AWS forces.
- Nothing public except the UI bucket's `s3:GetObject`. RDS is `publicly_accessible = false`, and security groups reference other security groups instead of `0.0.0.0/0`.
- Secrets: `manage_master_user_password = true`. Never put passwords in variables, outputs, or state. Mark sensitive outputs `sensitive = true`.
- Encrypt at rest: RDS storage and the S3 bucket (SSE-S3 at minimum).
- Tag everything through provider `default_tags`.
- Always run `terraform fmt -recursive` and `terraform validate`, then `terraform plan`. Never `apply` or `destroy`.
- Show the plan summary (add/change/destroy counts and any replacement) before declaring the work done.
