package io.github.adalberto.bedrockmcp.chat;

import java.util.List;

import io.github.adalberto.bedrockmcp.guardrail.GuardrailResult;

/**
 * Stable contract for every frontend (web, mobile, partners) behind API Gateway.
 */
public record ChatResponse(
        String conversationId,
        String answer,
        GuardrailInfo guardrail,
        Usage usage) {

    public record GuardrailInfo(GuardrailResult.Action input, GuardrailResult.Action output, List<String> reasons) {
    }

    public record Usage(Integer inputTokens, Integer outputTokens, Integer totalTokens, String model) {
        static Usage none() {
            return new Usage(0, 0, 0, null);
        }
    }
}
