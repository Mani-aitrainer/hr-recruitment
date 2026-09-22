# Spec: Infrastructure (dev) · HR Recruitment

Version 0.1 · Source proposal: `docs/proposal.md` §4, §5.4, §9–10

## Goal
One `terraform apply` in `iac/envs/dev` creates everything the job posting app needs in AWS.

## Out of scope
CloudFront, a custom domain, WAF, multiple environments, CI/CD, NAT gateway, Multi-AZ RDS.

## Resources

| Module | Resources | Key settings |
|---|---|---|
| `network` | VPC `10.20.0.0/16`, 2 private subnets in 2 AZs, security groups `lambda-sg` and `rds-sg`, interface VPC endpoint for Secrets Manager (private DNS on) | No internet gateway or NAT is needed for the Lambda. `rds-sg` allows 5432 from `lambda-sg` only. The endpoint SG allows 443 from `lambda-sg` only. |
| `database` | `aws_db_subnet_group`, `aws_db_instance` | PostgreSQL 17, `db.t4g.micro`, `allocated_storage = 20`, `storage_type = "gp3"`, `storage_encrypted = true`, `publicly_accessible = false`, `multi_az = false`, `manage_master_user_password = true`, `backup_retention_period = 1`, `deletion_protection = false` (dev), `skip_final_snapshot = true` (dev) |
| `api` | IAM role and policies, `aws_lambda_function` `hr-jobposting-api`, `aws_lambda_function` `hr-jobposting-db-migrate`, `aws_lambda_function_url`, CloudWatch log groups | `runtime = "java21"`, `architectures = ["arm64"]`, memory 1024 MB, timeout 15 s (migrate: 60 s), `reserved_concurrent_executions = 5`, VPC config with private subnets + `lambda-sg`, env vars `DB_HOST`, `DB_NAME`, `DB_SECRET_ARN`, `ALLOWED_ORIGIN`. Function URL: `authorization_type = "NONE"`, CORS origin = S3 website URL, methods GET/POST/PUT/PATCH/DELETE. Log retention 14 days. |
| `frontend` | S3 bucket, website configuration, public access block (relaxed for this bucket only), bucket policy (`s3:GetObject` for `*`) | `index_document = "index.html"`, `error_document = "index.html"` (Angular routing), versioning off, SSE-S3 |

## IAM (least privilege)
- API and migrate Lambda role:
  - `AWSLambdaVPCAccessExecutionRole` (managed): ENIs and CloudWatch Logs
  - Inline: `secretsmanager:GetSecretValue` on the RDS master secret ARN only
- No `*` resources except where AWS requires them (ENI actions in the managed policy).

## Inputs (`iac/envs/dev/terraform.tfvars`, git-ignored; commit `terraform.tfvars.example`)
| Variable | Default |
|---|---|
| `aws_region` | `us-east-1` |
| `project` | `hr-recruitment` |
| `env` | `dev` |
| `owner` | — (required) |
| `api_jar_path` | `../../../api/target/api.jar` |

## Outputs
`function_url`, `ui_website_url`, `ui_bucket_name`, `db_endpoint`, `db_secret_arn`, `migrate_function_name`

## Deployment order
1. `cd api && mvn -q package` → `api/target/api.jar`
2. `cd iac/envs/dev && terraform init && terraform plan -out tfplan` → review (the `iac-reviewer` agent)
3. `terraform apply tfplan` (a human runs this; Claude is denied it)
4. `aws lambda invoke --function-name $(terraform output -raw migrate_function_name) out.json` → runs Flyway migrations
5. Write `function_url` into `ui/src/environments/environment.ts`, then `cd ui && npm run build`
6. `aws s3 sync ui/dist/ui/browser s3://$(terraform output -raw ui_bucket_name) --delete`

## Documentation deliverable

The first Terraform work must also create `iac/README.md`: prerequisites (Terraform, AWS CLI, account permissions, the API JAR) with check commands, one-off backend setup, the numbered deploy order above as runnable commands with expected outputs, teardown, and troubleshooting. Keep it in step with the deployment order whenever it changes.

## Acceptance criteria
- **IAC-0** `iac/README.md` exists and its deploy steps match this spec.
- **IAC-1** `terraform validate` and `terraform fmt -check -recursive` pass.
- **IAC-2** `terraform plan` shows no resource with `publicly_accessible = true` other than the UI bucket policy.
- **IAC-3** From the browser, the UI loads and the list call to the Function URL succeeds with CORS.
- **IAC-4** Connecting to RDS from outside the VPC fails (the instance has no public address).
- **IAC-5** The Lambda role cannot read any secret other than the RDS master secret.
- **IAC-6** `terraform destroy` removes everything without manual steps.
