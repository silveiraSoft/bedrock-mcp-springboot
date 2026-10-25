package io.github.adalberto.bedrockmcp.catalog;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record Order(String orderId, String productId, int quantity, BigDecimal total, String currency,
        String status, Instant createdAt) implements Serializable {
}
