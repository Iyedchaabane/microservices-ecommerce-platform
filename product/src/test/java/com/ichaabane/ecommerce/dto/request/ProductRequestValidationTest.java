package com.ichaabane.ecommerce.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
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
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
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
    @DisplayName("Should reject a null available quantity (boxed Integer must not fall through to the primitive int entity field)")
    void shouldRejectNullQuantity() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", null, BigDecimal.ONE, 1))
                .contains("availableQuantity"));
    }

    @Test
    @DisplayName("Should accept a positive available quantity")
    void shouldAcceptPositiveQuantity() {
        assertTrue(validator.validate(
                new ProductRequest("Book", "A book", 7, BigDecimal.ONE, 1)).isEmpty());
    }

    @Test
    @DisplayName("Should accept the maximum int quantity without overflow at the DTO edge")
    void shouldAcceptMaxIntQuantity() {
        assertTrue(validator.validate(
                new ProductRequest("Book", "A book", Integer.MAX_VALUE, BigDecimal.ONE, 1)).isEmpty());
    }

    @Test
    @DisplayName("Should accept a zero available quantity (registered product awaiting restock)")
    void shouldAcceptZeroQuantity() {
        assertTrue(validator.validate(
                new ProductRequest("Book", "A book", 0, BigDecimal.ONE, 1)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a negative quantity")
    void shouldRejectNegativeQuantity() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", -3, BigDecimal.ONE, 1))
                .contains("availableQuantity"));
    }

    @Test
    @DisplayName("Should accept Integer.MIN_VALUE-typed input only via the negative rule")
    void shouldRejectMostNegativeIntegerQuantity() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", Integer.MIN_VALUE, BigDecimal.ONE, 1))
                .contains("availableQuantity"));
    }

    @Test
    @DisplayName("Should reject a zero price (strictly positive pricing preserved)")
    void shouldRejectZeroPrice() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 10, BigDecimal.ZERO, 1)).contains("price"));
    }

    @Test
    @DisplayName("Should reject a negative price")
    void shouldRejectNegativePrice() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 10, BigDecimal.valueOf(-1), 1)).contains("price"));
    }

    @Test
    @DisplayName("Should reject a null price")
    void shouldRejectNullPrice() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 10, null, 1)).contains("price"));
    }

    @Test
    @DisplayName("Should reject an extremely small non-positive price")
    void shouldRejectTinyFractionalNegativePrice() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 10, BigDecimal.valueOf(-0.01), 1)).contains("price"));
    }

    @Test
    @DisplayName("Should accept a one-cent price as the lower valid bound")
    void shouldAcceptOneCentPrice() {
        assertTrue(validator.validate(
                new ProductRequest("Book", "A book", 10, BigDecimal.valueOf(0.01), 1)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null category id")
    void shouldRejectNullCategory() {
        assertTrue(violatedFields(new ProductRequest("Book", "d", 10, BigDecimal.ONE, null)).contains("categoryId"));
    }

    @Test
    @DisplayName("Should accept a negative category id at the DTO edge (existence is the service's 404 check)")
    void shouldNotConstraintCategorySignAtDtoLayer() {
        assertTrue(validator.validate(
                new ProductRequest("Book", "A book", 10, BigDecimal.ONE, -1)).isEmpty());
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
    @DisplayName("Should reject a name over 255 chars")
    void shouldRejectTooLongName() {
        assertTrue(violatedFields(new ProductRequest("x".repeat(256), "d", 10, BigDecimal.ONE, 1)).contains("name"));
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
    @DisplayName("Should report one violation per invalid field")
    void shouldReportAllInvalidFields() {
        Set<?> violations = validator.validate(new ProductRequest(null, null, -1, BigDecimal.valueOf(-1), null));
        assertEquals(5, violations.size());
        assertFalse(violations.isEmpty());
    }
}
