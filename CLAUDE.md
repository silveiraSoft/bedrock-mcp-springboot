# CLAUDE.md — bedrock-mcp-springboot

## Project specifics
- Build/test: `./mvnw verify` (Windows: `mvnw.cmd verify`). Local infra: `docker compose up -d`.
- Domain rules: public repo (no secrets, credentials or AWS account ids), read-only tools in release 1, guardrails ON,
  ES/EN, AWS SDK aligned with Spring AI, no prompt logging in qa/prod, Floci + real Bedrock locally.
