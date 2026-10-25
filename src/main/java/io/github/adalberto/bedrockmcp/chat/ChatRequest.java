package io.github.adalberto.bedrockmcp.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId optional; send back the value returned by the previous response to keep context
 * @param message        natural-language instruction, e.g. "Do you have tents in stock under $400?"
 */
public record ChatRequest(
        @Pattern(regexp = "[A-Za-z0-9-]{1,64}") String conversationId,
        @NotBlank @Size(max = 4000) String message) {
}
