package io.github.adalberto.bedrockmcp.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Single, typed source of truth for application settings ({@code app.*}).
 * Every value has a default in {@code application.yml}; profiles override only what changes.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Aws aws,
        @DefaultValue BackendApi backendApi,
        @DefaultValue Guardrail guardrail,
        @DefaultValue Chat chat,
        @DefaultValue Security security,
        @DefaultValue Secrets secrets) {

    /** How the application obtains AWS credentials. */
    public enum CredentialsMode {
        /**
         * AWS SDK default chain. Recommended everywhere:
         * ECS task role (QA/PROD), AWS_PROFILE + `aws sso login` (local), env vars (CI).
         */
        DEFAULT,
        /** Named profile from ~/.aws/config, typically an IAM Identity Center (SSO) profile. */
        PROFILE,
        /** Static keys from env/secret. Break-glass only - never in QA/PROD. */
        STATIC,
        /** Dummy credentials for the Floci emulator (no real AWS calls). */
        FLOCI
    }

    public record Aws(
            @DefaultValue("us-east-1") String region,
            @DefaultValue("DEFAULT") CredentialsMode credentialsMode,
            String profile,
            String accessKeyId,
            String secretAccessKey,
            String sessionToken,
            /** When set, Bedrock calls go to this endpoint (e.g. Floci http://localhost:4566). */
            String bedrockEndpoint,
            /** When set, Secrets Manager calls go to this endpoint (e.g. Floci). */
            String secretsManagerEndpoint,
            @DefaultValue("60s") Duration apiCallTimeout) {
    }

    public record BackendApi(
            @DefaultValue("http://localhost:8080") String baseUrl,
            @DefaultValue("2s") Duration connectTimeout,
            @DefaultValue("10s") Duration readTimeout,
            /** Forward the caller's bearer token to the backend API (on-behalf-of). */
            @DefaultValue("true") boolean propagateAuthorization) {
    }

    public record Guardrail(
            /** BEDROCK = Amazon Bedrock Guardrails ApplyGuardrail API, LOCAL = offline regex/denylist, NONE = off. */
            @DefaultValue("LOCAL") GuardrailMode mode,
            String guardrailId,
            @DefaultValue("DRAFT") String guardrailVersion,
            @DefaultValue("true") boolean checkOutput,
            @DefaultValue("Sorry, I can't help with that request.") String blockedMessage,
            @DefaultValue List<String> deniedTopics) {
    }

    public enum GuardrailMode { BEDROCK, LOCAL, NONE }

    public record Chat(
            @DefaultValue("classpath:prompts/system-prompt.st") String systemPromptLocation,
            @DefaultValue("4000") int maxUserMessageChars) {
    }

    public record Security(
            /** false only for local development. QA/PROD validate JWTs (Cognito) as defence in depth behind API Gateway. */
            @DefaultValue("false") boolean enabled) {
    }

    public record Secrets(
            @DefaultValue("false") boolean enabled,
            /** Secrets Manager secret holding a JSON object with application secrets. */
            String secretId) {
    }
}
