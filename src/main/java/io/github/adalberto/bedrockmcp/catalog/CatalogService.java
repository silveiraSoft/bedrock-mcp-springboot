package io.github.adalberto.bedrockmcp.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * SAMPLE business logic with an in-memory store, so the project runs with zero infrastructure.
 * Replace with your real REST API (or DynamoDB/Aurora repository). The LLM never touches this class
 * directly: it goes through HTTP ({@code CatalogApiClient}) exactly as it would against a remote API.
 */
@Service
public class CatalogService {

    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public CatalogService() {
        List.of(
                new Product("P-100", "Trail Running Shoes", "footwear", new BigDecimal("129.90"), "USD", 25),
                new Product("P-101", "Waterproof Hiking Boots", "footwear", new BigDecimal("189.00"), "USD", 8),
                new Product("P-200", "Insulated Jacket", "apparel", new BigDecimal("219.50"), "USD", 0),
                new Product("P-201", "Merino Base Layer", "apparel", new BigDecimal("79.00"), "USD", 40),
                new Product("P-300", "Ultralight Tent 2P", "camping", new BigDecimal("399.00"), "USD", 5),
                new Product("P-301", "Sleeping Bag -7C / 20F", "camping", new BigDecimal("249.00"), "USD", 12))
            .forEach(p -> products.put(p.id(), p));
    }

    public List<Product> search(String query, String category) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        String c = category == null ? "" : category.toLowerCase(Locale.ROOT).trim();
        return products.values().stream()
                .filter(p -> c.isEmpty() || p.category().equalsIgnoreCase(c))
                .filter(p -> q.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(q)
                        || p.category().toLowerCase(Locale.ROOT).contains(q))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .limit(20)
                .toList();
    }

    public Optional<Product> find(String id) {
        return Optional.ofNullable(products.get(id));
    }

    public Order createOrder(CreateOrderRequest request) {
        Product product = find(request.productId())
                .orElseThrow(() -> new NotFoundException("Product " + request.productId() + " not found"));
        synchronized (this) {
            Product current = products.get(product.id());
            if (current.stock() < request.quantity()) {
                throw new BusinessRuleException("Insufficient stock for " + product.id() + ": available "
                        + current.stock() + ", requested " + request.quantity());
            }
            products.put(current.id(), new Product(current.id(), current.name(), current.category(),
                    current.price(), current.currency(), current.stock() - request.quantity()));
        }
        Order order = new Order("O-" + UUID.randomUUID().toString().substring(0, 8), product.id(),
                request.quantity(), product.price().multiply(BigDecimal.valueOf(request.quantity())),
                product.currency(), "CONFIRMED", Instant.now());
        orders.put(order.orderId(), order);
        return order;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) {
            super(message);
        }
    }
}
