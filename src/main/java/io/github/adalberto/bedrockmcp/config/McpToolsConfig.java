package io.github.adalberto.bedrockmcp.config;

import io.github.adalberto.bedrockmcp.tools.CatalogTools;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@code @Tool} methods as MCP tools. Spring AI's MCP server auto-configuration discovers every
 * {@link ToolCallbackProvider} bean and publishes it on {@code /mcp} (Streamable HTTP / stateless).
 */
@Configuration(proxyBeanMethods = false)
public class McpToolsConfig {

    @Bean
    ToolCallbackProvider catalogToolCallbacks(CatalogTools catalogTools) {
        return MethodToolCallbackProvider.builder().toolObjects(catalogTools).build();
    }
}
