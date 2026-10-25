package io.github.adalberto.bedrockmcp.tools;

import java.util.List;

import io.github.adalberto.bedrockmcp.backend.CatalogGateway;
import io.github.adalberto.bedrockmcp.catalog.Order;
import io.github.adalberto.bedrockmcp.catalog.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * The ONLY operations the LLM (and external MCP clients) can perform against the REST API.
 *
 * <p>Design rules (see docs/adr/0004-guardrails-defense-in-depth.md):
 * <ul>
 *   <li>Explicit allow-list: one method per business capability, never a generic "call any URL" tool.</li>
 *   <li>Small, validated inputs; bounded outputs (max 20 rows) to control tokens and data exposure.</li>
 *   <li>Write tools state their side effects and require explicit user confirmation in the description.</li>
 *   <li>Every call goes through {@link CatalogGateway} with the end user's identity (token propagation).</li>
 * </ul>
 * Exposed in-process to the chat endpoint and remotely via MCP ({@code /mcp}) - one definition, two transports.
 */
@Component
public class CatalogTools {

    private static final Logger log = LoggerFactory.getLogger(CatalogTools.class);
    static final int MAX_QUANTITY = 10;

    private final CatalogGateway gateway;

    public CatalogTools(CatalogGateway gateway) {
        this.gateway = gateway;
    }

    @Tool(name = "search_products",
            description = "Search the product catalog. Use it to answer questions about available products, "
                    + "prices, categories or stock. Returns at most 20 products.")
    public List<Product> searchProducts(
            @ToolParam(description = "Free-text search over product name/category, e.g. 'tent'. Optional.",
                    required = false) String query,
            @ToolParam(description = "Exact category filter: footwear, apparel or camping. Optional.",
                    required = false) String category) {
        log.info("tool=search_products query='{}' category='{}'", query, category);
        return gateway.search(blankToNull(query), blankToNull(category));
    }

    @Tool(name = "get_product",
            description = "Get one product by its id (format P-123), including current price and stock.")
    public Product getProduct(@ToolParam(description = "Product id, e.g. P-100") String productId) {
        log.info("tool=get_product id={}", productId);
        return gateway.get(requireProductId(productId));
    }

    @Tool(name = "create_order",
            description = "Create a purchase order. SIDE EFFECT: reserves stock and creates a real order. "
                    + "Only call this after the user has explicitly confirmed product and quantity in the "
                    + "conversation. Quantity must be between 1 and " + MAX_QUANTITY + ".")
    public Order createOrder(
            @ToolParam(description = "Product id, e.g. P-100") String productId,
            @ToolParam(description = "Units to order, 1-" + MAX_QUANTITY) int quantity) {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("quantity must be between 1 and " + MAX_QUANTITY);
        }
        log.info("tool=create_order id={} quantity={}", productId, quantity);
        return gateway.createOrder(requireProductId(productId), quantity);
    }

    static String requireProductId(String productId) {
        if (productId == null || !productId.matches("P-\\d{1,6}")) {
            throw new IllegalArgumentException("productId must match P-<digits>");
        }
        return productId;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
