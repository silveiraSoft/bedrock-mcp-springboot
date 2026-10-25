package io.github.adalberto.bedrockmcp.backend;

import java.util.List;

import io.github.adalberto.bedrockmcp.catalog.CreateOrderRequest;
import io.github.adalberto.bedrockmcp.catalog.Order;
import io.github.adalberto.bedrockmcp.catalog.Product;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Anti-corruption layer between the LLM tools and the REST API.
 * <ul>
 *   <li>Read operations are cached (Valkey / ElastiCache) - LLMs repeat the same lookups a lot.</li>
 *   <li>Transient failures are retried (Spring Framework 7 {@code @Retryable}); 4xx are not retried.</li>
 *   <li>Write operations are never cached nor retried automatically (not idempotent).</li>
 * </ul>
 */
@Service
public class CatalogGateway {

    private final CatalogApiClient client;

    public CatalogGateway(CatalogApiClient client) {
        this.client = client;
    }

    @Cacheable(cacheNames = "product-search", key = "(#query ?: '') + '|' + (#category ?: '')")
    @Retryable(excludes = HttpClientErrorException.class, maxRetries = 2, delay = 200)
    public List<Product> search(String query, String category) {
        return List.copyOf(client.searchProducts(query, category));
    }

    @Cacheable(cacheNames = "product", key = "#id")
    @Retryable(excludes = HttpClientErrorException.class, maxRetries = 2, delay = 200)
    public Product get(String id) {
        return client.getProduct(id);
    }

    /** Stock changes after an order, so cached reads are evicted. */
    @CacheEvict(cacheNames = { "product", "product-search" }, allEntries = true)
    public Order createOrder(String productId, int quantity) {
        return client.createOrder(new CreateOrderRequest(productId, quantity));
    }
}
