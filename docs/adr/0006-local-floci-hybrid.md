# ADR-0006: Local environment — Floci for AWS infrastructure, real Bedrock for AI (hybrid)

- **Status:** Accepted by owner (session 002, 2026-10-24) (DISCOVERY D9: Floci for infra, real AWS account for Bedrock)
- **Date:** 2026-10-24

## Context
We want to develop offline and free where possible. Floci (open-source AWS emulator, port 4566) emulates Secrets
Manager, SSM, DynamoDB, ElastiCache, Cognito, API Gateway, ECS, ECR, IAM, STS and more. Its **Bedrock Runtime is a
stub** (fixed answer; tool use not round-tripped; streaming partial) with an optional **proxy** to an
OpenAI-compatible backend, and **Guardrails/ApplyGuardrail is not emulated**.

## Decision
- `local` profile: Floci for Secrets Manager/SSM, Valkey container for cache, **real Amazon Bedrock** (dev account)
  through IAM Identity Center for LLM + Guardrails. Per-client credentials: Floci clients get dummy keys,
  Bedrock gets the SSO identity (`AwsClientsConfig#credentialsFor`).
- `local,local-offline` profile: Ollama (tool-calling model) + `LocalGuardrailService` — no AWS account.
- `local,floci-bedrock` profile: Bedrock calls to Floci stub — wiring/smoke tests in CI without AWS.

## Consequences
- Real AI behaviour (tool use, guardrails) is only verified against real Bedrock → keep a cheap dev account with a
  budget alarm. Haiku 4.5 + prompt caching keeps dev cost low.
- Floci image tag must be pinned once the setup is stable.
