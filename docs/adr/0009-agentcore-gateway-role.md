# ADR-0009: Role of Amazon Bedrock AgentCore Gateway

- **Status:** Accepted by owner (session 002, 2026-10-24) — Option C (hybrid)
- **Date:** 2026-10-24
- **Inputs:** DISCOVERY-001 (D1 sample domain → real API later, D2 frontends + MCP agents, D4 read-only, D5 single account, D6 Cognito, D9 Floci + real Bedrock)
- **Supersedes (if accepted):** the "phase 2" note in ADR-0008

## Context
We need (a) natural-language `/chat` for frontends and (b) an MCP endpoint for external AI agents, both read-only
over a REST API that will later be replaced by a real one. AgentCore Gateway is AWS's managed MCP front door:
- Aggregates **targets** into one virtual MCP server: OpenAPI specs, Lambda, Smithy, API Gateway REST stages, and **remote MCP servers**.
- **Inbound auth**: OAuth/JWT (Cognito works), IAM SigV4. **Outbound auth**: OAuth 2LO/3LO, API key, IAM SigV4 (SigV4 only to targets that verify it — not to an ALB).
- **Policy** (tool-level RBAC per JWT claims), **rate limits** per tool / JWT claim, **semantic tool search**, private targets via **VPC Lattice**.
- Quotas: 200 tool calls/s per gateway (adjustable), 15 min invocation timeout, 100 targets/gateway.
- Price: ≈ USD 0.005 per 1,000 invocations, 0.025 per 1,000 searches, 0.02 per 100 tools indexed/month → negligible at our scale.
- Terraform: `aws_bedrockagentcore_gateway` / `aws_bedrockagentcore_gateway_target` exist in the AWS provider.
- **Floci emulates the AgentCore control plane only** (create gateways/targets); the data plane is a canned stub →
  no real MCP traffic through the Gateway locally. OpenAPI targets also cannot reach `localhost`.

## Options
| # | Option | Pros | Cons |
|---|---|---|---|
| A | **No AgentCore** — Spring AI MCP server in the app, exposed through API Gateway | One codebase, fully testable locally, cheapest, portable | We build/operate MCP auth, per-tool authorization, rate limiting and discovery ourselves; MCP over API Gateway has timeout/streaming quirks; every new API = new Java code |
| B | **AgentCore Gateway with OpenAPI target** (no MCP code; tools generated from the REST API spec) | Zero tool code; swap sample API → real API by swapping the spec | Tool descriptions/validation come from OpenAPI (weaker LLM-facing quality control); no `oneOf/anyOf`; cannot run locally; `/chat` would need to call the Gateway as an MCP client (extra hop, latency) or duplicate tools; private ALB needs Lattice + OAuth/API key outbound |
| C | **Hybrid (recommended)** — tools implemented once in Spring (`@Tool`, unit-tested, local `/mcp`); in QA/PROD **AgentCore Gateway is the front door for external agents** with the Spring `/mcp` as an **MCP-server target** (private via VPC Lattice, outbound OAuth client-credentials). `/chat` uses the same tools **in-process** (no Gateway hop) | Single tool implementation; local dev and CI unaffected; managed OAuth, tool-level Policy (enforce read-only per client), rate limits, audit and semantic search for agents; future APIs added as more targets without touching this service; MCP traffic leaves API Gateway (no MCP timeout quirks) | One more managed service + VPC Lattice to learn and pay for; two auth paths (API GW for frontends, AgentCore for agents) |
| D | AgentCore **Runtime** to host the service | Session-isolated microVMs, consumption pricing | Designed for agent code, not a REST API + chat service; less control over the JVM; doesn't remove the need for API Gateway for frontends |

Also evaluated and **not used**: **AgentCore Memory** for conversation history — minimum event expiration is
7 days while our requirement is short-term (≈24 h TTL) and the cache store (Valkey) already exists.

## Decision
**Option C.** Yes to AgentCore Gateway, in a precise role: managed MCP gateway for **external agents** in QA/PROD.
Not used for `/chat` (in-process tools are faster and keep Guardrails in one place), not used as the tool
implementation layer (OpenAPI target) in release 1, not AgentCore Runtime, not AgentCore Memory.

## Consequences
- The release 1 infrastructure milestone adds: gateway, MCP-server target with `privateEndpoint` (managed Lattice) to the
  internal ALB, Cognito M2M app client + scopes for agents, Policy allowing only read tools, rate limits per client.
- App: `/mcp` requires a JWT from the Gateway's outbound OAuth client (not from end users); `/api/v1/chat` requires end-user JWT.
- Local: agents and tests call Spring `/mcp` directly; Floci can be used to test Terraform for the Gateway control plane.
- Revisit Option B when the real API has a clean OpenAPI spec and we want non-Java teams to add tools without code.
