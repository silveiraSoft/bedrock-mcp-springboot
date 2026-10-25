package io.github.adalberto.bedrockmcp.guardrail;

/** Disabled guardrail. Never use outside a local experiment. */
public class NoopGuardrailService implements GuardrailService {

    @Override
    public GuardrailResult evaluate(String text, Source source) {
        return GuardrailResult.pass(text);
    }
}
