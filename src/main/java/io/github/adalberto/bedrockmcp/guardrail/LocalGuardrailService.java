package io.github.adalberto.bedrockmcp.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Offline, deterministic guardrail used when Bedrock Guardrails is not reachable (Floci does not emulate
 * ApplyGuardrail). It is a SAFETY NET for local development and unit tests - not a replacement for
 * Amazon Bedrock Guardrails in QA/PROD.
 *
 * <ul>
 *   <li>Blocks common prompt-injection / jailbreak phrases (INPUT).</li>
 *   <li>Blocks configured denied topics (INPUT and OUTPUT).</li>
 *   <li>Masks e-mails, US SSNs, card-like numbers and AWS access key ids (INPUT and OUTPUT).</li>
 * </ul>
 */
public class LocalGuardrailService implements GuardrailService {

    private static final List<Pattern> INJECTION = List.of(
            Pattern.compile("ignore (all |any )?(previous|prior|above) (instructions|rules)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(reveal|print|show) (me )?(your|the) (system )?prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now (in )?(developer|dan|jailbreak) mode", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (your|the) (guidelines|policies|rules)", Pattern.CASE_INSENSITIVE));

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b");
    private static final Pattern AWS_KEY = Pattern.compile("\\b(AKIA|ASIA)[A-Z0-9]{16}\\b");

    private final List<String> deniedTopics;
    private final String blockedMessage;

    public LocalGuardrailService(List<String> deniedTopics, String blockedMessage) {
        this.deniedTopics = deniedTopics.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        this.blockedMessage = blockedMessage;
    }

    @Override
    public GuardrailResult evaluate(String text, Source source) {
        if (text == null || text.isBlank()) {
            return GuardrailResult.pass(text);
        }
        List<String> reasons = new ArrayList<>();
        if (source == Source.INPUT && INJECTION.stream().anyMatch(p -> p.matcher(text).find())) {
            reasons.add("PROMPT_ATTACK");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        deniedTopics.stream().filter(lower::contains).forEach(t -> reasons.add("DENIED_TOPIC:" + t));
        if (!reasons.isEmpty()) {
            return new GuardrailResult(GuardrailResult.Action.BLOCKED, blockedMessage, List.copyOf(reasons));
        }

        String masked = text;
        masked = mask(masked, AWS_KEY, "{AWS_ACCESS_KEY}", "AWS_ACCESS_KEY", reasons);
        masked = mask(masked, EMAIL, "{EMAIL}", "EMAIL", reasons);
        masked = mask(masked, SSN, "{US_SOCIAL_SECURITY_NUMBER}", "US_SSN", reasons);
        masked = mask(masked, CARD, "{CREDIT_DEBIT_CARD_NUMBER}", "CARD_NUMBER", reasons);
        return reasons.isEmpty()
                ? GuardrailResult.pass(text)
                : new GuardrailResult(GuardrailResult.Action.MASKED, masked, List.copyOf(reasons));
    }

    private static String mask(String text, Pattern pattern, String replacement, String reason, List<String> reasons) {
        var matcher = pattern.matcher(text);
        if (matcher.find()) {
            reasons.add("PII:" + reason);
            return matcher.replaceAll(replacement);
        }
        return text;
    }
}
