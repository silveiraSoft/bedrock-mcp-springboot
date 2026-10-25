# Security policy

## Reporting a vulnerability
Please **do not open a public issue**. Use GitHub's *Security → Report a vulnerability* (private advisory) on this
repository. You will get an answer within 7 days.

## Scope and practices
- No credentials are stored in this repository. AWS access uses IAM Identity Center (developers), IAM roles
  (ECS tasks) and OIDC (GitHub Actions). Secret scanning and push protection are enabled.
- LLM safety: Amazon Bedrock Guardrails on input/output, read-only tools, token propagation (see docs/adr/0004).
- Supported versions: the latest release on `main`.
