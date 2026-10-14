package com.ichaabane.ecommerce.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductPurchaseRequest(
        @NotNull(message = "Product is mandatory")
        @Positive(message = "Product id must be positive")
        Integer productId,
        // quantity is a boxed Integer (mirrors ProductRequest.availableQuantity),
        // so it needs an explicit @NotNull: @Positive ignores nulls and would
        // otherwise let a missing quantity through to an NPE on unboxing.
        @NotNull(message = "Quantity is mandatory")
        @Positive(message = "Quantity must be positive")
        Integer quantity
) {
}
