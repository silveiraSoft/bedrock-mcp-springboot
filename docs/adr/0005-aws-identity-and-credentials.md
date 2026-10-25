# ADR-0005: AWS identity — IAM Identity Center for humans, IAM roles for workloads, OIDC for CI

- **Status:** Accepted by owner (session 002, 2026-10-24) — adapted to a single AWS account
- **Date:** 2026-10-24

## Context
The app needs AWS credentials for Bedrock, Secrets Manager, etc. in local, QA and PROD. Long-lived access keys are
the #1 cause of AWS credential leaks.

## Decision
| Who | Mechanism | Config in app |
|---|---|---|
| Developer laptop | **IAM Identity Center (AWS SSO)** permission set `BedrockDeveloper` in the *dev* account; `aws sso login --profile bedrock-dev` | `credentials-mode: DEFAULT` + `AWS_PROFILE` (or `PROFILE` + `app.aws.profile`) |
| ECS task (QA/PROD) | **ECS task role** (least privilege: `bedrock:InvokeModel*`/`Converse*` on the specific inference profile + models, `bedrock:ApplyGuardrail` on the guardrail ARN, `secretsmanager:GetSecretValue` on the app secret) | `credentials-mode: DEFAULT` |
| ECS agent | **Task execution role** (ECR pull, CloudWatch logs, inject `secrets`) | — |
| GitHub Actions | **OIDC federation** → `gha-deploy-<env>` role restricted to repo + branch/environment | — |
| Emergency only | STATIC keys from env, short-lived, rotated | `credentials-mode: STATIC` (logs a warning) |

AWS accounts: separate **dev / qa / prod** accounts under AWS Organizations (Control Tower recommended), Bedrock
model access enabled per account, SCPs to restrict regions and deny IAM user access keys.

## Consequences
- No AWS secrets in the repo, in `.env`, or in Secrets Manager. Application secrets (3rd-party API keys) are the
  only thing stored in Secrets Manager.
- The Docker container locally mounts `~/.aws` to reuse the SSO session.
- SDK modules `sso` + `ssooidc` are required on the classpath for SSO profiles (already in `pom.xml`).

## Owner review (session 002)
Single AWS account for now (DISCOVERY D5), region **us-east-1**, budget alarm USD 30. Environments QA and PROD are
separate Terraform stacks and VPCs with name prefixes (`bmcp-qa-*`, `bmcp-prod-*`), mandatory tags (`env`, `app`,
`owner`), IAM roles scoped per environment with permission boundaries, and IAM Identity Center for humans. **Risk R1:**
blast radius shared between QA and PROD → move PROD to its own account (AWS Organizations) before real users.
Public repository: no account IDs/ARNs committed; use variables and GitHub environment secrets.
