# Security rules (all folders)

- Never read, print, or commit `.env`, `*.tfvars`, `*.tfstate`, credentials, or keys.
- Never paste secrets, tokens, or personal data into code, logs, tests, docs, or commit messages.
- Validate input on the server even when the UI already validates it.
- New dependencies: prefer well-maintained libraries and state why they are needed.
- If a task needs broader permissions (IAM, CORS, public access), stop and explain the trade-off before changing it.
