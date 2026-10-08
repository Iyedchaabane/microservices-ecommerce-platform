package com.ichaabane.ecommerce.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProductPurchaseRequest Bean Validation Tests")
class ProductPurchaseRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> violatedFields(ProductPurchaseRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Should accept a valid purchase line")
    void shouldAcceptValid() {
        assertTrue(validator.validate(new ProductPurchaseRequest(1, 2)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null product id")
    void shouldRejectNullProductId() {
        assertTrue(violatedFields(new ProductPurchaseRequest(null, 2)).contains("productId"));
    }

    @Test
    @DisplayName("Should reject a zero quantity")
    void shouldRejectZeroQuantity() {
        assertTrue(violatedFields(new ProductPurchaseRequest(1, 0)).contains("quantity"));
    }

    @Test
    @DisplayName("Should reject a negative quantity")
    void shouldRejectNegativeQuantity() {
        assertTrue(violatedFields(new ProductPurchaseRequest(1, -2)).contains("quantity"));
    }

    @Test
    @DisplayName("Should reject a NaN quantity")
    void shouldRejectNaNQuantity() {
        assertTrue(violatedFields(new ProductPurchaseRequest(1, Double.NaN)).contains("quantity"));
    }

    @Test
    @DisplayName("Should accept a fractional positive quantity")
    void shouldAcceptFractionalQuantity() {
        assertFalse(violatedFields(new ProductPurchaseRequest(1, 2.5)).contains("quantity"));
    }
}
