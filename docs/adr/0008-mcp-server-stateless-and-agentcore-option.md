# ADR-0008: MCP server in the Spring Boot app (stateless Streamable HTTP); AgentCore Gateway as phase-2 option

- **Status:** Accepted in part — external exposure superseded by ADR-0009 (session 002)
- **Date:** 2026-10-24

## Context
We want external AI agents (Claude Desktop/Code, IDEs, Bedrock agents) to use the same capabilities as the chat
endpoint. Spring AI 2.0 ships MCP Java SDK 2.x with Streamable HTTP (default) and a STATELESS mode.

## Options
1. **Spring AI MCP server in the same service** — one tool definition (`@Tool`) serves `/api/v1/chat` (in-process)
   and `/mcp` (remote). Stateless mode → any Fargate task can answer, no sticky sessions.
2. **Amazon Bedrock AgentCore Gateway** — converts OpenAPI/Lambda/API Gateway targets into MCP tools, inbound OAuth,
   outbound credential management, semantic tool search, private targets via VPC Lattice. Zero MCP code, but newer
   service and more AWS lock-in.
3. Separate MCP microservice — more ops for no gain at this size.

## Decision
Option 1 now (`spring.ai.mcp.server.protocol=STATELESS`, protected by JWT in QA/PROD). Keep `docs/api/openapi.yaml`
accurate so Option 2 can be adopted without code changes when there are multiple APIs/agents.

## Consequences
- MCP endpoint must never be anonymous outside local (Spring AI warns HTTP transports are unauthenticated by default).
- Stateless servers cannot push server-initiated notifications — acceptable for request/response tools.

## Owner review (session 002)
Kept: tools implemented once in Spring (`@Tool`) and served by the in-app stateless MCP server. Changed: external
agents reach it through **AgentCore Gateway** (MCP-server target), not through API Gateway — see ADR-0009.
