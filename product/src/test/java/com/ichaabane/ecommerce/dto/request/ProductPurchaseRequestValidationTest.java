package com.ichaabane.ecommerce.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProductPurchaseRequest Bean Validation Tests")
class ProductPurchaseRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
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
    @DisplayName("Should reject a zero product id (@Positive, service double-checks)")
    void shouldRejectZeroProductId() {
        assertFalse(validator.validate(new ProductPurchaseRequest(0, 1)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null product id")
    void shouldRejectNullProductId() {
        assertTrue(violatedFields(new ProductPurchaseRequest(null, 2)).contains("productId"));
    }

    @Test
    @DisplayName("Should reject a negative product id")
    void shouldRejectNegativeProductId() {
        assertFalse(violatedFields(new ProductPurchaseRequest(-1, 2)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null quantity (boxed Integer must not fall through to the primitive int entity field)")
    void shouldRejectNullQuantity() {
        assertTrue(violatedFields(new ProductPurchaseRequest(1, null)).contains("quantity"));
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
    @DisplayName("Should reject Integer.MIN_VALUE quantity without overflow errors")
    void shouldRejectExtremeNegativeQuantity() {
        Set<String> fields = violatedFields(new ProductPurchaseRequest(1, Integer.MIN_VALUE));
        assertTrue(fields.contains("quantity"));
        assertEquals(1, fields.size());
    }
}
