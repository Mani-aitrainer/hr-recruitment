# IaC · Terraform (AWS)

Creates everything in `docs/specs/infrastructure.spec.md`: VPC, private subnets, Secrets Manager VPC endpoint, RDS PostgreSQL (`db.t4g.micro`, 20 GB gp3), IAM roles, Java 21 Lambdas with a Function URL, and S3 static website hosting for the UI.

## Layout
```
modules/network/     VPC, subnets, security groups, Secrets Manager endpoint
modules/database/    RDS PostgreSQL, subnet group
modules/api/         IAM role, API + db-migrate Lambdas, Function URL, log groups
modules/frontend/    S3 website bucket and policy
envs/dev/            main.tf (wires the modules), backend.tf, variables.tf, outputs.tf, terraform.tfvars.example
```

## Commands (run in `envs/dev`)
`terraform init` · `terraform fmt -recursive` · `terraform validate` · `terraform plan -out tfplan`
**A human** runs `terraform apply tfplan` and `terraform destroy`.

## Conventions
- Each module has `main.tf`, `variables.tf`, `outputs.tf`, and `versions.tf`
- Every variable has a `description` and `type`. Every resource gets tags through provider `default_tags`.
- Name resources `${var.project}-${var.env}-<purpose>`
- Prefer `aws_iam_policy_document` data sources over inline JSON strings

Rules: `.claude/rules/terraform.md` · Skill: `terraform-change` · Reviewer: `iac-reviewer` agent
Spec: @../docs/specs/infrastructure.spec.md
