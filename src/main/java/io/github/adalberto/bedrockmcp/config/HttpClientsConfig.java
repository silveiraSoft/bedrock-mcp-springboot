package io.github.adalberto.bedrockmcp.config;

import io.github.adalberto.bedrockmcp.backend.CatalogApiClient;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
public class HttpClientsConfig {

    @Bean
    CatalogApiClient catalogApiClient(RestClient.Builder builder, AppProperties props, AppSecrets secrets) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.backendApi().connectTimeout());
        requestFactory.setReadTimeout(props.backendApi().readTimeout());

        RestClient restClient = builder
                .baseUrl(props.backendApi().baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor(apiKeyInterceptor(secrets))
                .requestInterceptor(authorizationPropagation(props.backendApi().propagateAuthorization()))
                .build();

        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(CatalogApiClient.class);
    }

    /** Optional static API key for the backend (e.g. an internal API Gateway usage-plan key) from Secrets Manager. */
    private static ClientHttpRequestInterceptor apiKeyInterceptor(AppSecrets secrets) {
        return (request, body, execution) -> {
            secrets.get("backend-api-key").ifPresent(key -> request.getHeaders().set("x-api-key", key));
            return execution.execute(request, body);
        };
    }

    /**
     * On-behalf-of: the LLM acts with the END USER's permissions, never with more.
     * Tools execute synchronously on the request thread, so the inbound Authorization header is available.
     */
    private static ClientHttpRequestInterceptor authorizationPropagation(boolean enabled) {
        return (request, body, execution) -> {
            if (enabled && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest inbound = attrs.getRequest();
                String authorization = inbound.getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
            return execution.execute(request, body);
        };
    }
}
