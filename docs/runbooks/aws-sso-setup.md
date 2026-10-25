# Runbook — Connect local development to real AWS with IAM Identity Center (AWS SSO)

Use this when Floci is not enough (Bedrock LLM + Guardrails). No access keys are created.

## One-time (AWS admin)
1. AWS Organizations → enable **IAM Identity Center** in your home region.
2. Create a permission set `BedrockDeveloper` (session 8 h) with an inline policy like:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Sid": "InvokeModels", "Effect": "Allow",
      "Action": ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream", "bedrock:Converse", "bedrock:ConverseStream"],
      "Resource": [
        "arn:aws:bedrock:*::foundation-model/anthropic.claude-haiku-4-5-*",
        "arn:aws:bedrock:*:<DEV_ACCOUNT_ID>:inference-profile/us.anthropic.claude-haiku-4-5-*"
      ] },
    { "Sid": "Guardrails", "Effect": "Allow",
      "Action": ["bedrock:ApplyGuardrail", "bedrock:GetGuardrail", "bedrock:ListGuardrails",
                 "bedrock:CreateGuardrail", "bedrock:UpdateGuardrail", "bedrock:CreateGuardrailVersion"],
      "Resource": "*" }
  ]
}
```
3. Assign your user/group to the **dev** account with that permission set.
4. Bedrock console (dev account, `us-east-1`) → *Model access* → enable **Anthropic Claude Haiku 4.5** (first-time use form).
5. Create a **budget alarm** (e.g. $20/month) on the dev account.

## Each developer (once)
```bash
aws configure sso --profile bedrock-dev
#  SSO session name: my-sso
#  SSO start URL:    https://<your-portal>.awsapps.com/start
#  SSO region:       us-east-1
#  Account / role:   <dev account> / BedrockDeveloper
#  Default region:   us-east-1
```
## Each day
```bash
aws sso login --profile bedrock-dev
aws sts get-caller-identity --profile bedrock-dev   # sanity check
```
Then run the app:
- IntelliJ: env `AWS_PROFILE=bedrock-dev`, active profile `local`.
- Compose: `AWS_PROFILE=bedrock-dev` in `.env` (Windows: also `AWS_DIR=C:/Users/<you>/.aws`).

## Troubleshooting
| Error | Fix |
|---|---|
| `Token has expired and refresh failed` | `aws sso login --profile bedrock-dev` |
| `To use Sso related properties … add 'sso' and 'ssooidc'` | already in pom; rebuild |
| `AccessDeniedException … model access` | enable model access in Bedrock console / check inference profile ARN in policy |
| `ValidationException: on-demand throughput isn't supported` | use the inference profile id `us.anthropic…` not the raw model id |
