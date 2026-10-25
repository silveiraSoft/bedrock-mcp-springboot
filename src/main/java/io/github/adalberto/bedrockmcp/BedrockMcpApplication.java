package io.github.adalberto.bedrockmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Natural-language gateway over a REST API.
 *
 * <ul>
 *   <li>{@code /api/v1/products}, {@code /api/v1/orders} - sample business REST API (replace with yours).</li>
 *   <li>{@code /api/v1/chat} - natural language endpoint: Bedrock Guardrails -> LLM (Bedrock Converse) -> tools -> REST API.</li>
 *   <li>{@code /mcp} - MCP server (Streamable HTTP / stateless) exposing the same tools to external agents.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableResilientMethods
public class BedrockMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(BedrockMcpApplication.class, args);
    }
}
