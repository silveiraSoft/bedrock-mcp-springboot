# ADR-0007: Caching strategy

- **Status:** Accepted by owner (session 002, 2026-10-24)
- **Date:** 2026-10-24

## Decision
| Layer | What | Where |
|---|---|---|
| Bedrock **prompt caching** | System prompt + tool schemas (`SYSTEM_AND_TOOLS`) | Bedrock (up to ~90% cheaper cached input tokens, lower latency) |
| **Tool/API read cache** | `product` (5 min), `product-search` (1 min); evicted on `create_order` | ElastiCache Serverless for Valkey (local: Valkey container) via Spring Cache |
| Conversation memory | Last 20 messages per `conversationId` | In-memory now → **move to Valkey or DynamoDB before scaling > 1 task** (stateless tasks behind ALB) |
| API Gateway cache | Not used for `/chat` (POST, non-deterministic) | Optional for public GET endpoints |
| Semantic cache (similar questions → same answer) | Not now — risk of cross-user leakage and stale answers | Re-evaluate with per-tenant keys |

## Consequences
- Cached values are user-agnostic catalog data only. Never cache user-specific responses under shared keys.
- ElastiCache: TLS on, IAM auth or AUTH token from Secrets Manager, in private subnets.

## Owner review (session 002)
Accepted. **Valkey also stores the short-term conversation memory** (last 20 messages, TTL 24 h), so the service
stays stateless across Fargate tasks. ElastiCache Serverless for Valkey in AWS; Valkey container locally.
