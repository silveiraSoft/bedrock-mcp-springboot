package io.github.adalberto.bedrockmcp.catalog;

import java.io.Serializable;
import java.math.BigDecimal;

/** Serializable because it is cached in Valkey/Redis. */
public record Product(String id, String name, String category, BigDecimal price, String currency, int stock)
        implements Serializable {
}
