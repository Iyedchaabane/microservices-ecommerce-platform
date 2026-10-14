package com.ichaabane.ecommerce.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,
        @NotBlank(message = "Product description is required")
        @Size(max = 255, message = "Product description must not exceed 255 characters")
        String description,
        @NotNull(message = "Available quantity is required")
        @Min(value = 0, message = "Available quantity must be zero or more")
        Integer availableQuantity,
        @NotNull(message = "Product price is required")
        @Positive(message = "Price should be positive")
        BigDecimal price,
        @NotNull(message = "Product category is required")
        Integer categoryId
) {
}
