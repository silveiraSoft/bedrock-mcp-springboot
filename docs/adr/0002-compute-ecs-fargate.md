# ADR-0002: Run the service on Amazon ECS Fargate (ARM64/Graviton)

- **Status:** Accepted by owner (session 002, 2026-10-24)
- **Date:** 2026-10-24

## Context
Workload profile: Spring Boot JVM service, natural-language requests that run an LLM tool loop (typically 3–30 s,
worst case ~60 s), an MCP Streamable HTTP endpoint, calls to Bedrock/Valkey/REST API inside a VPC, multiple
frontends with steady daytime traffic. Team: 1–3 devs, Terraform + GitHub Actions.

## Options considered
| Option | Assessment |
|---|---|
| **ECS Fargate** | No servers/AMIs, per-task IAM role, private subnets, ALB health checks, native blue/green deployments, autoscaling on CPU/requests. JVM stays warm → no cold starts. Graviton ARM64 ≈ 20% cheaper. Minimum cost: 2 always-on tasks. |
| AWS Lambda (+SnapStart) | Great for spiky, short calls. Here: tool loops can exceed API Gateway's default 29 s integration timeout, MCP sessions/streaming fit poorly, per-invocation cost grows with long LLM waits (you pay while waiting on Bedrock). Rejected for this service. |
| App Runner | Closed to new customers (AWS recommends ECS Express Mode). Rejected. |
| ECS Express Mode | One API call creates Fargate + ALB + autoscaling. Good for sandboxes. It provisions its own ALB, which conflicts with "API Gateway + private ALB" control. Acceptable for an early QA env only. |
| EKS | Powerful, but cluster ops/cost not justified for 1–3 services. |
| Bedrock AgentCore Runtime | Purpose-built for agents/MCP servers (session isolation, consumption pricing). Newer, more lock-in, less control over the JVM. Keep as Phase-2 option (ADR-0008). |

## Decision
ECS Fargate, `linux/arm64`, min 2 tasks across AZs (PROD), 1 task + Fargate Spot (QA), 1 vCPU / 2 GB to start,
target-tracking on CPU 60% and ALB requests/target. Native ECS blue/green deployments with circuit breaker.
Container stopTimeout 60 s; app graceful shutdown 30 s.

## Consequences
- Always-on baseline cost (~2 small tasks). Acceptable for an API used by several frontends.
- Image must be multi-arch (Dockerfile + `buildx`).
- Long LLM calls: set ALB idle timeout ≥ 120 s and API Gateway integration timeout raised accordingly (REST API regional allows > 29 s by quota request) or move to streaming responses.

## Revisit when
Traffic is extremely spiky/low (Lambda cheaper), or the platform grows to many agents (AgentCore), or > 10 services (EKS).

## Owner review (session 002)
Accepted with traffic = unknown (DISCOVERY D11). Cost controls: QA runs 1 task with Fargate Spot and is scaled to 0
outside working hours (scheduled scaling); PROD min 2 tasks. Note: the USD 30/month budget alarm (session 002) covers
release 1 (local + Bedrock usage only). Before the infrastructure milestone deploys QA, re-baseline the budget (expected USD 100–150/month
for always-on infra, less with scale-to-zero).
