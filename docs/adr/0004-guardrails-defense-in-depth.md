# ADR-0004: Guardrails and LLM security — defence in depth

- **Status:** Accepted by owner (session 002, 2026-10-24)
- **Date:** 2026-10-24

## Context
An LLM can be manipulated (direct/indirect prompt injection), can leak PII, produce harmful content, or call tools
it should not. OWASP Top 10 for LLM Applications applies (LLM01 prompt injection, LLM02 sensitive info disclosure,
LLM06 excessive agency, LLM10 unbounded consumption).

## Decision — layers
1. **Edge:** WAF managed rules + rate limiting; API Gateway usage plans/throttling per frontend.
2. **Identity:** Cognito JWT at API Gateway and in the app; the end-user token is **propagated** to the REST API,
   so the LLM can never do more than the user could (no privileged service account for tools).
3. **Amazon Bedrock Guardrails via `ApplyGuardrail`:**
   - INPUT before the model: content filters incl. **PROMPT_ATTACK (HIGH)**, denied topics, word filters, PII.
   - OUTPUT before the user: PII **anonymize/block**, toxicity, denied topics. (Contextual grounding check optional.)
   - Numbered guardrail version per environment, managed by Terraform. DRAFT only locally.
4. **Tool design (excessive agency):** explicit allow-list of narrow tools, validated inputs (`P-\d+`, quantity 1–10),
   bounded outputs (≤ 20 rows), write tools require explicit user confirmation (system prompt) and are audited.
5. **System prompt hardening:** tool results are untrusted data; no disclosure of rules.
6. **Consumption limits:** max input chars, `max-tokens`, Bedrock timeouts, per-client quotas.
7. **Observability:** guardrail decisions logged as reason codes (never the content), Bedrock invocation logging to
   S3 with restricted access, CloudWatch alarms on guardrail intervention rate.
8. **Offline fallback:** `LocalGuardrailService` (regex/deny-list) for local/offline only — never QA/PROD.

## Consequences
- Each chat request costs 2 extra ApplyGuardrail calls (priced per text unit) — accepted for safety.
- Floci cannot emulate Guardrails → local dev uses LOCAL mode or a real dev guardrail.

## Owner review (session 002)
Accepted. Additions: Guardrail **Standard tier** (Spanish + English, prompt-leakage detection; requires cross-Region
inference); red-team set in ES and EN; release 1 is **read-only** (no write tools exist); AgentCore Policy restricts
agents to read tools; tool loop bounded (≤ 5 tool calls, 20 s).
