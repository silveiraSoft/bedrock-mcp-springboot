package io.github.adalberto.bedrockmcp.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(@NotBlank String productId, @Min(1) @Max(10) int quantity) {
}
