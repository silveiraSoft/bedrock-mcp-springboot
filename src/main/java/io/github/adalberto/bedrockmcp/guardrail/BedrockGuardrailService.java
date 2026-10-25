package io.github.adalberto.bedrockmcp.guardrail;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAssessment;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailOutputContent;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock;

/**
 * Amazon Bedrock Guardrails via the model-independent {@code ApplyGuardrail} API.
 *
 * <p>Why ApplyGuardrail instead of only attaching the guardrail to Converse:
 * <ul>
 *   <li>The INPUT is screened BEFORE any tokens are spent and before any tool can run.</li>
 *   <li>Works the same for any model (Bedrock, Ollama offline, a future provider).</li>
 *   <li>Lets us use the sanitized (PII-anonymized) prompt downstream and audit the decision.</li>
 * </ul>
 * Configure the guardrail itself (content filters incl. PROMPT_ATTACK, denied topics, PII, word filters,
 * contextual grounding) with Terraform - see infra/guardrail and docs/runbooks/guardrail-setup.md.
 */
public class BedrockGuardrailService implements GuardrailService {

    private static final Logger log = LoggerFactory.getLogger(BedrockGuardrailService.class);
    private static final String BLOCKED = "BLOCKED";

    private final BedrockRuntimeClient client;
    private final String guardrailId;
    private final String guardrailVersion;
    private final String blockedMessage;

    public BedrockGuardrailService(BedrockRuntimeClient client, String guardrailId, String guardrailVersion,
            String blockedMessage) {
        if (guardrailId == null || guardrailId.isBlank()) {
            throw new IllegalStateException("app.guardrail.guardrail-id is required when app.guardrail.mode=BEDROCK");
        }
        this.client = client;
        this.guardrailId = guardrailId;
        this.guardrailVersion = guardrailVersion;
        this.blockedMessage = blockedMessage;
    }

    @Override
    public GuardrailResult evaluate(String text, Source source) {
        if (text == null || text.isBlank()) {
            return GuardrailResult.pass(text);
        }
        ApplyGuardrailResponse response = client.applyGuardrail(r -> r
                .guardrailIdentifier(guardrailId)
                .guardrailVersion(guardrailVersion)
                .source(source == Source.INPUT ? GuardrailContentSource.INPUT : GuardrailContentSource.OUTPUT)
                .content(GuardrailContentBlock.fromText(GuardrailTextBlock.builder().text(text).build())));

        if (response.action() != GuardrailAction.GUARDRAIL_INTERVENED) {
            return GuardrailResult.pass(text);
        }

        List<String> reasons = reasons(response.assessments());
        String guardrailText = response.outputs().stream()
                .map(GuardrailOutputContent::text)
                .reduce("", (a, b) -> a + b);
        boolean blocked = reasons.stream().anyMatch(r -> r.endsWith(":" + BLOCKED)) || guardrailText.isBlank();

        log.info("guardrail intervened source={} blocked={} reasons={}", source, blocked, reasons);
        return blocked
                ? new GuardrailResult(GuardrailResult.Action.BLOCKED,
                        guardrailText.isBlank() ? blockedMessage : guardrailText, reasons)
                : new GuardrailResult(GuardrailResult.Action.MASKED, guardrailText, reasons);
    }

    /** Flattens assessments into "POLICY:TYPE:ACTION" strings (no user content is included). */
    private static List<String> reasons(List<GuardrailAssessment> assessments) {
        List<String> reasons = new ArrayList<>();
        for (GuardrailAssessment a : assessments) {
            if (a.topicPolicy() != null) {
                a.topicPolicy().topics().forEach(t ->
                        reasons.add("TOPIC:" + t.name() + ":" + t.actionAsString()));
            }
            if (a.contentPolicy() != null) {
                a.contentPolicy().filters().forEach(f ->
                        reasons.add("CONTENT:" + f.typeAsString() + ":" + f.actionAsString()));
            }
            if (a.wordPolicy() != null) {
                a.wordPolicy().customWords().forEach(w -> reasons.add("WORD:CUSTOM:" + w.actionAsString()));
                a.wordPolicy().managedWordLists().forEach(w ->
                        reasons.add("WORD:" + w.typeAsString() + ":" + w.actionAsString()));
            }
            if (a.sensitiveInformationPolicy() != null) {
                a.sensitiveInformationPolicy().piiEntities().forEach(p ->
                        reasons.add("PII:" + p.typeAsString() + ":" + p.actionAsString()));
                a.sensitiveInformationPolicy().regexes().forEach(rx ->
                        reasons.add("REGEX:" + rx.name() + ":" + rx.actionAsString()));
            }
        }
        return List.copyOf(reasons);
    }
}
