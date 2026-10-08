package com.ichaabane.ecommerce.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProductRequest Bean Validation Tests")
class ProductRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> violatedFields(ProductRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Should accept a fully valid request")
    void shouldAcceptValidRequest() {
        assertTrue(validator.validate(
                new ProductRequest("Book", "A book", 10, BigDecimal.valueOf(15), 1)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null name")
    void shouldRejectNullName() {
        assertTrue(violatedFields(new ProductRequest(null, "d", 10, BigDecimal.ONE, 1)).contains("name"));
    }

    @Test
    @DisplayName("Should reject a blank name")
    void shouldRejectBlankName() {
        assertTrue(violatedFields(new ProductRequest("  ", "d", 10, BigDecimal.ONE, 1)).contains("name"));
    }

    @Test
    @DisplayName("Should reject a null description")
    void shouldRejectNullDescription() {
        assertTrue(violatedFields(new ProductRequest("Book", null, 10, BigDecimal.ONE, 1)).contains("description"));
    }

    @Test
    @DisplayName("Should reject a blank description")
    void shouldRejectBlankDescription() {
        assertTrue(violatedFields(new ProductRequest("Book", " ", 10, BigDecimal.ONE, 1)).contains("description"));
    }

    @Test
    @DisplayName("Should reject a zero quantity")
    void shouldRejectZeroQuantity() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 0, BigDecimal.ONE, 1)).contains("availableQuantity"));
    }

    @Test
    @DisplayName("Should reject a negative quantity")
    void shouldRejectNegativeQuantity() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", -3, BigDecimal.ONE, 1)).contains("availableQuantity"));
    }

    @Test
    @DisplayName("Should reject a NaN quantity as not positive")
    void shouldRejectNaNQuantity() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", Double.NaN, BigDecimal.ONE, 1)).contains("availableQuantity"));
    }

    @Test
    @DisplayName("Should reject a zero price")
    void shouldRejectZeroPrice() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 10, BigDecimal.ZERO, 1)).contains("price"));
    }

    @Test
    @DisplayName("Should reject a negative price")
    void shouldRejectNegativePrice() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 10, BigDecimal.valueOf(-1), 1)).contains("price"));
    }

    @Test
    @DisplayName("Should accept a null price because there is no @NotNull on price")
    void shouldAcceptNullPrice() {
        assertTrue(validator.validate(new ProductRequest("Book", "d", 10, null, 1)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null category id")
    void shouldRejectNullCategory() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 10, BigDecimal.ONE, null)).contains("categoryId"));
    }

    @Test
    @DisplayName("Should report one violation per invalid field")
    void shouldReportAllInvalidFields() {
        Set<?> violations = validator.validate(new ProductRequest(null, null, 0, BigDecimal.valueOf(-1), null));
        assertEquals(5, violations.size());
        assertFalse(violations.isEmpty());
    }
}
