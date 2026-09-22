---
name: terraform-change
description: Writes or changes Terraform in iac/ for the HR recruitment AWS stack (VPC, RDS PostgreSQL, Lambda with Function URL, IAM, Secrets Manager endpoint, S3 website). Use when adding or modifying any AWS resource, module, variable, or output.
---
# Terraform change

## Steps
1. Read `docs/specs/infrastructure.spec.md` and find the module the change belongs to (`network`, `database`, `api`, or `frontend`).
2. If unsure about an argument, look it up (aws-docs MCP, or the provider docs for AWS provider 6.x). Don't guess argument names.
3. Make the change in the module, and expose any new inputs and outputs through `envs/dev`.
4. Apply `.claude/rules/terraform.md`: least-privilege IAM, no public access except the UI bucket, encryption, and tags.
5. In `iac/envs/dev` run:
   - `terraform fmt -recursive`
   - `terraform init -backend=false` (if not yet initialised)
   - `terraform validate`
   - `terraform plan -out tfplan` (only if AWS credentials are configured)
6. Summarise the plan: counts to add, change, and destroy, and any **replacement** or deletion of stateful resources (RDS, S3) in bold.
7. Ask the `iac-reviewer` agent to review the diff and plan.
8. Stop. A human runs `terraform apply tfplan`.

## Done when
- `iac/README.md` exists (create it in this task if missing) with: prerequisites and check commands, one-off backend and tfvars setup, the numbered deploy order, verification, teardown, and troubleshooting. Update it whenever the deploy order changes.
- fmt and validate are clean
- The plan matches the spec's resource table, with no unexpected replacements
- The reviewer reports no Critical or High findings
