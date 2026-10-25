# infra/ — Terraform (next phase)

Planned layout (to be implemented in the infrastructure milestone). Kept here so the repo structure is stable from day one.

```
infra/
  bootstrap/            # once per account: S3 state bucket (native lockfile), GitHub OIDC provider, deploy roles
  modules/
    network/            # VPC 3 AZ, private subnets, VPC endpoints (bedrock-runtime, secretsmanager, ecr.api, ecr.dkr, logs, sts, s3 gw)
    ecr/                # repo bedrock-mcp-springboot, scan on push, lifecycle policy, immutable tags
    ecs-service/        # cluster, task def (ARM64), task + execution roles, ALB (internal), autoscaling, blue/green
    api-gateway/        # REST API (regional), VPC Link v2 -> ALB, Cognito authorizer, usage plans, WAF association, custom domain
    cognito/            # user pool, app clients (frontends), M2M client for MCP agents, resource server scopes
    cache/              # ElastiCache Serverless for Valkey
    bedrock/            # aws_bedrock_guardrail + version (uses guardrail/*.json), model invocation logging
    observability/      # log groups, alarms (5xx, latency, guardrail interventions, Bedrock throttling), dashboard
  envs/
    dev/ qa/ prod/      # one root module per env/account, *.tfvars, backend config
  guardrail/            # guardrail policies (JSON) shared by CLI runbook and Terraform
```

Task-role policy (least privilege) sketch:
```json
{
  "Statement": [
    { "Effect": "Allow", "Action": ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream", "bedrock:Converse", "bedrock:ConverseStream"],
      "Resource": ["arn:aws:bedrock:*:${account}:inference-profile/us.anthropic.claude-haiku-4-5-*",
                   "arn:aws:bedrock:*::foundation-model/anthropic.claude-haiku-4-5-*"] },
    { "Effect": "Allow", "Action": "bedrock:ApplyGuardrail", "Resource": "${guardrail_arn}" },
    { "Effect": "Allow", "Action": "secretsmanager:GetSecretValue", "Resource": "${app_secret_arn}" }
  ]
}
```

GitHub Actions (later): `ci.yml` (verify + image build) → `deploy.yml` with environments `qa` (auto on main) and
`prod` (manual approval), OIDC `aws-actions/configure-aws-credentials`, push to ECR with immutable tag
`<version>-<sha>`, Terraform plan on PR / apply on merge.
