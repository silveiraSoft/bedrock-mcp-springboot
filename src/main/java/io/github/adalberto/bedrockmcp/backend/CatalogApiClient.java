package io.github.adalberto.bedrockmcp.backend;

import java.util.List;

import io.github.adalberto.bedrockmcp.catalog.CreateOrderRequest;
import io.github.adalberto.bedrockmcp.catalog.Order;
import io.github.adalberto.bedrockmcp.catalog.Product;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for the business REST API that the LLM is allowed to use.
 * Point {@code app.backend-api.base-url} at your real API (e.g. the internal ALB / Cloud Map name in AWS).
 */
@HttpExchange("/api/v1")
public interface CatalogApiClient {

    @GetExchange("/products")
    List<Product> searchProducts(@RequestParam(required = false) String query,
            @RequestParam(required = false) String category);

    @GetExchange("/products/{id}")
    Product getProduct(@PathVariable String id);

    @PostExchange("/orders")
    Order createOrder(@RequestBody CreateOrderRequest request);
}
