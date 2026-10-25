package io.github.adalberto.bedrockmcp.guardrail;

import java.util.List;

/**
 * @param action  NONE (pass through), MASKED (text was sanitized, e.g. PII anonymized), BLOCKED
 * @param text    text to use downstream: original, masked, or the blocked message
 * @param reasons machine-readable reasons (policy names), never the offending content
 */
public record GuardrailResult(Action action, String text, List<String> reasons) {

    public enum Action { NONE, MASKED, BLOCKED }

    public static GuardrailResult pass(String text) {
        return new GuardrailResult(Action.NONE, text, List.of());
    }

    public boolean blocked() {
        return action == Action.BLOCKED;
    }
}
