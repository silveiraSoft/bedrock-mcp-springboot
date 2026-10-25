package io.github.adalberto.bedrockmcp.guardrail;

import io.github.adalberto.bedrockmcp.config.AppProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration(proxyBeanMethods = false)
public class GuardrailConfig {

    private static final Logger log = LoggerFactory.getLogger(GuardrailConfig.class);

    @Bean
    GuardrailService guardrailService(AppProperties props, BedrockRuntimeClient bedrockRuntimeClient) {
        AppProperties.Guardrail g = props.guardrail();
        log.info("Guardrail mode: {}", g.mode());
        return switch (g.mode()) {
            case BEDROCK -> new BedrockGuardrailService(bedrockRuntimeClient, g.guardrailId(), g.guardrailVersion(),
                    g.blockedMessage());
            case LOCAL -> new LocalGuardrailService(g.deniedTopics(), g.blockedMessage());
            case NONE -> {
                log.warn("Guardrails are DISABLED (app.guardrail.mode=NONE)");
                yield new NoopGuardrailService();
            }
        };
    }
}
