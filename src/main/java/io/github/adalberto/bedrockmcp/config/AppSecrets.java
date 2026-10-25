package io.github.adalberto.bedrockmcp.config;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Application secrets (NOT AWS credentials).
 *
 * <p>Resolution order for a key such as {@code backend-api-key}:
 * <ol>
 *   <li>Environment / Spring property {@code BACKEND_API_KEY} - this is how ECS injects Secrets Manager
 *       values into the container ({@code secrets:} block of the task definition). Preferred for QA/PROD.</li>
 *   <li>The JSON secret {@code app.secrets.secret-id} read from AWS Secrets Manager at startup
 *       (Floci locally, real Secrets Manager on AWS).</li>
 * </ol>
 *
 * <p>Values are never logged.
 */
@Component
public class AppSecrets {

    private static final Logger log = LoggerFactory.getLogger(AppSecrets.class);

    private final Environment environment;
    private final Map<String, String> secretValues;

    public AppSecrets(AppProperties props, SecretsManagerClient secretsManager, JsonMapper jsonMapper,
            Environment environment) {
        this.environment = environment;
        this.secretValues = load(props.secrets(), secretsManager, jsonMapper);
    }

    public Optional<String> get(String key) {
        String envKey = key.toUpperCase().replace('-', '_').replace('.', '_');
        String fromEnv = environment.getProperty(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(fromEnv);
        }
        return Optional.ofNullable(secretValues.get(key));
    }

    private static Map<String, String> load(AppProperties.Secrets cfg, SecretsManagerClient client, JsonMapper json) {
        if (!cfg.enabled() || cfg.secretId() == null || cfg.secretId().isBlank()) {
            log.info("Secrets Manager lookup disabled (app.secrets.enabled=false)");
            return Collections.emptyMap();
        }
        try {
            String raw = client.getSecretValue(r -> r.secretId(cfg.secretId())).secretString();
            Map<String, String> values = json.readValue(raw, new TypeReference<Map<String, String>>() { });
            log.info("Loaded {} application secret key(s) from Secrets Manager secret '{}'", values.size(), cfg.secretId());
            return Map.copyOf(values);
        }
        catch (RuntimeException ex) {
            // Fail fast: a misconfigured secret must stop the deployment (ECS circuit breaker rolls back).
            throw new IllegalStateException("Could not read secret '" + cfg.secretId() + "' from Secrets Manager", ex);
        }
    }
}
