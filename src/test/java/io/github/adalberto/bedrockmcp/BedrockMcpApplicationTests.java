package io.github.adalberto.bedrockmcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Boots the full application with no external infrastructure (no AWS, no Redis, no Floci):
 * guardrail LOCAL, cache in-memory, Bedrock client created lazily (never called).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cache.type=simple",
        "management.health.redis.enabled=false",
        "app.aws.credentials-mode=FLOCI",
        "app.guardrail.mode=LOCAL"
})
class BedrockMcpApplicationTests {

    @Value("${local.server.port}")
    int port;

    @Test
    void restApiIsServed() {
        String body = RestClient.create("http://localhost:" + port)
                .get().uri("/api/v1/products?category=footwear")
                .retrieve().body(String.class);
        assertThat(body).contains("P-100").doesNotContain("P-300");
    }

    @Test
    void mcpServerListsTheCatalogTools() {
        String body = RestClient.create("http://localhost:" + port)
                .post().uri("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header("MCP-Protocol-Version", "2025-06-18")
                .body("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                        """)
                .retrieve().body(String.class);
        assertThat(body).contains("search_products").contains("get_product").contains("create_order");
    }
}
