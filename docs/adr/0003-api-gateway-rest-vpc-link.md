# ADR-0003: Front the service with API Gateway REST API (Regional) + VPC Link v2 → internal ALB

- **Status:** Accepted by owner (session 002, 2026-10-24) — scope narrowed to frontends
- **Date:** 2026-10-24

## Context
The API will be consumed by several frontends (web, mobile, partners) and by MCP clients. Needs: authentication,
per-client quotas, WAF, custom domain, versioning, and no public exposure of the containers.

## Options considered
1. **API Gateway REST API (v1, Regional)** — Cognito/JWT authorizers, **usage plans + API keys per frontend**,
   **AWS WAF** integration, request validation, stage caching, private integration with **ALB via VPC Link v2**
   (no NLB needed anymore), response streaming support.
2. **API Gateway HTTP API (v2)** — cheaper and simpler JWT authorizer, but **no WAF attachment and no usage plans**.
3. **ALB public + WAF only** — cheapest, but no per-client quotas/API keys/developer-facing API management.
4. **CloudFront in front** — useful later for global edge/caching of public GETs; not needed for a POST chat API.

## Decision
REST API (Regional) + WAF (AWS managed rule groups + rate-based rule) + Cognito authorizer + usage plans per
frontend + VPC Link v2 → internal ALB → ECS. The app validates the JWT again (defence in depth).

## Consequences
- API-GW stage cache is NOT used for `/chat` (POST, non-deterministic). Caching is done in the app (ADR-0007).
- Raise the integration timeout for `/chat` (default 29 s) or adopt streaming before long prompts go live.
- OpenAPI spec (`docs/api/openapi.yaml`) is the contract for Terraform import and for AgentCore Gateway (phase 2).

## Owner review (session 002)
Accepted for **frontends (`/api/v1/chat`) only**. External MCP agents no longer go through API Gateway: they use
AgentCore Gateway (ADR-0009).
