package io.github.adalberto.bedrockmcp.config;

import java.net.URI;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * One place that decides HOW we talk to AWS, for every environment.
 *
 * <p>The {@link AwsCredentialsProvider}, {@link AwsRegionProvider}, {@link BedrockRuntimeClient} and
 * {@link BedrockRuntimeAsyncClient} beans defined here are picked up by Spring AI's Bedrock Converse
 * auto-configuration (it declares them {@code @ConditionalOnMissingBean} / {@code ObjectProvider}),
 * so the LLM calls and our Guardrail calls always share the same identity and endpoint.
 *
 * <table>
 *   <tr><th>Environment</th><th>credentials-mode</th><th>Identity</th></tr>
 *   <tr><td>local (IDE / compose)</td><td>DEFAULT or PROFILE</td><td>IAM Identity Center profile after {@code aws sso login}</td></tr>
 *   <tr><td>local-offline</td><td>FLOCI</td><td>dummy keys, everything emulated</td></tr>
 *   <tr><td>QA / PROD (ECS Fargate)</td><td>DEFAULT</td><td>ECS task IAM role - no secrets at all</td></tr>
 * </table>
 */
@Configuration(proxyBeanMethods = false)
public class AwsClientsConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsClientsConfig.class);

    /** Floci accepts any credentials; these never leave your machine. */
    static final AwsCredentialsProvider FLOCI_CREDENTIALS =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));

    @Bean
    AwsCredentialsProvider awsCredentialsProvider(AppProperties props) {
        AppProperties.Aws aws = props.aws();
        log.info("AWS credentials mode: {}", aws.credentialsMode());
        return switch (aws.credentialsMode()) {
            case DEFAULT -> DefaultCredentialsProvider.builder().build();
            case PROFILE -> {
                if (!StringUtils.hasText(aws.profile())) {
                    throw new IllegalStateException("app.aws.profile is required when credentials-mode=PROFILE");
                }
                yield ProfileCredentialsProvider.builder().profileName(aws.profile()).build();
            }
            case STATIC -> {
                log.warn("Using STATIC AWS credentials. This is a break-glass option - prefer IAM Identity Center or roles.");
                if (!StringUtils.hasText(aws.accessKeyId()) || !StringUtils.hasText(aws.secretAccessKey())) {
                    throw new IllegalStateException("app.aws.access-key-id and app.aws.secret-access-key are required for STATIC");
                }
                yield StringUtils.hasText(aws.sessionToken())
                        ? StaticCredentialsProvider.create(AwsSessionCredentials.create(
                                aws.accessKeyId(), aws.secretAccessKey(), aws.sessionToken()))
                        : StaticCredentialsProvider.create(AwsBasicCredentials.create(
                                aws.accessKeyId(), aws.secretAccessKey()));
            }
            case FLOCI -> FLOCI_CREDENTIALS;
        };
    }

    @Bean
    AwsRegionProvider awsRegionProvider(AppProperties props) {
        Region region = Region.of(props.aws().region());
        return () -> region;
    }

    @Bean(destroyMethod = "close")
    BedrockRuntimeClient bedrockRuntimeClient(AppProperties props, AwsCredentialsProvider credentials,
            AwsRegionProvider region) {
        var builder = BedrockRuntimeClient.builder()
                .credentialsProvider(credentialsFor(props.aws().bedrockEndpoint(), credentials))
                .region(region.getRegion())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(props.aws().apiCallTimeout()))
                .overrideConfiguration(c -> c.apiCallTimeout(props.aws().apiCallTimeout()));
        endpoint(props.aws().bedrockEndpoint()).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient(AppProperties props, AwsCredentialsProvider credentials,
            AwsRegionProvider region) {
        var builder = BedrockRuntimeAsyncClient.builder()
                .credentialsProvider(credentialsFor(props.aws().bedrockEndpoint(), credentials))
                .region(region.getRegion())
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .readTimeout(props.aws().apiCallTimeout()))
                .overrideConfiguration(c -> c.apiCallTimeout(props.aws().apiCallTimeout()));
        endpoint(props.aws().bedrockEndpoint()).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    SecretsManagerClient secretsManagerClient(AppProperties props, AwsCredentialsProvider credentials,
            AwsRegionProvider region) {
        var builder = SecretsManagerClient.builder()
                .credentialsProvider(credentialsFor(props.aws().secretsManagerEndpoint(), credentials))
                .region(region.getRegion())
                .httpClientBuilder(ApacheHttpClient.builder());
        endpoint(props.aws().secretsManagerEndpoint()).ifPresent(builder::endpointOverride);
        return builder.build();
    }

    /**
     * Hybrid local mode: when a service points to Floci we use dummy credentials for THAT client only,
     * so Secrets Manager can be emulated while Bedrock uses your real SSO identity.
     */
    private static AwsCredentialsProvider credentialsFor(String endpointOverride, AwsCredentialsProvider real) {
        return StringUtils.hasText(endpointOverride) ? FLOCI_CREDENTIALS : real;
    }

    private static java.util.Optional<URI> endpoint(String value) {
        return StringUtils.hasText(value) ? java.util.Optional.of(URI.create(value)) : java.util.Optional.empty();
    }
}
