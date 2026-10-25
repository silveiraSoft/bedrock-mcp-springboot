package io.github.adalberto.bedrockmcp.guardrail;

/**
 * Screens text entering (user prompt) and leaving (model answer) the LLM.
 * Implementations: {@link BedrockGuardrailService} (Amazon Bedrock Guardrails, QA/PROD and local with AWS),
 * {@link LocalGuardrailService} (offline, deterministic), {@link NoopGuardrailService}.
 */
public interface GuardrailService {

    GuardrailResult evaluate(String text, Source source);

    enum Source { INPUT, OUTPUT }
}
