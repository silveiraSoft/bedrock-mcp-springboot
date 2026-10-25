package io.github.adalberto.bedrockmcp.config;

import java.time.Duration;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Per-cache TTLs. Product details change rarely (5 min); searches are short-lived (1 min).
 * Locally Valkey runs in docker compose; on AWS use ElastiCache Serverless for Valkey (TLS, IAM auth).
 */
@Configuration(proxyBeanMethods = false)
public class CacheConfig {

    @Bean
    RedisCacheManagerBuilderCustomizer cacheTtls() {
        return builder -> builder
                .withCacheConfiguration("product",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("product-search",
                        RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(1)));
    }
}
