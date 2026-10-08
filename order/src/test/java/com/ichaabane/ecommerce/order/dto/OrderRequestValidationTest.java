package com.ichaabane.ecommerce.order.dto;

import com.ichaabane.ecommerce.order.model.PaymentMethod;
import com.ichaabane.ecommerce.product.PurchaseRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OrderRequest Bean Validation Tests")
class OrderRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> violatedPaths(OrderRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private OrderRequest validRequest() {
        return new OrderRequest("REF-1", PaymentMethod.CREDIT_CARD, "customer-1",
                List.of(new PurchaseRequest(21, 2)));
    }

    @Test
    @DisplayName("Should accept a fully valid order request")
    void shouldAcceptValid() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null reference")
    void shouldRejectNullReference() {
        var request = new OrderRequest(null, PaymentMethod.VISA, "c1", List.of(new PurchaseRequest(1, 1)));
        assertTrue(violatedPaths(request).contains("reference"));
    }

    @Test
    @DisplayName("Should reject a blank reference")
    void shouldRejectBlankReference() {
        var request = new OrderRequest("  ", PaymentMethod.VISA, "c1", List.of(new PurchaseRequest(1, 1)));
        assertTrue(violatedPaths(request).contains("reference"));
    }

    @Test
    @DisplayName("Should reject a null payment method")
    void shouldRejectNullPaymentMethod() {
        var request = new OrderRequest("REF-1", null, "c1", List.of(new PurchaseRequest(1, 1)));
        assertTrue(violatedPaths(request).contains("paymentMethod"));
    }

    @Test
    @DisplayName("Should reject a null customer id")
    void shouldRejectNullCustomerId() {
        var request = new OrderRequest("REF-1", PaymentMethod.VISA, null, List.of(new PurchaseRequest(1, 1)));
        assertTrue(violatedPaths(request).contains("customerId"));
    }

    @Test
    @DisplayName("Should reject an empty customer id")
    void shouldRejectEmptyCustomerId() {
        var request = new OrderRequest("REF-1", PaymentMethod.VISA, "", List.of(new PurchaseRequest(1, 1)));
        assertTrue(violatedPaths(request).contains("customerId"));
    }

    @Test
    @DisplayName("Should reject a blank customer id")
    void shouldRejectBlankCustomerId() {
        var request = new OrderRequest("REF-1", PaymentMethod.VISA, "   ", List.of(new PurchaseRequest(1, 1)));
        assertTrue(violatedPaths(request).contains("customerId"));
    }

    @Test
    @DisplayName("Should reject a null product list")
    void shouldRejectNullProducts() {
        var request = new OrderRequest("REF-1", PaymentMethod.VISA, "c1", null);
        assertTrue(violatedPaths(request).contains("products"));
    }

    @Test
    @DisplayName("Should reject an empty product list")
    void shouldRejectEmptyProducts() {
        var request = new OrderRequest("REF-1", PaymentMethod.VISA, "c1", List.of());
        assertTrue(violatedPaths(request).contains("products"));
    }

    @Test
    @DisplayName("Should reject a nested purchase line without a product id")
    void shouldRejectNestedNullProductId() {
        var request = new OrderRequest("REF-1", PaymentMethod.VISA, "c1", List.of(new PurchaseRequest(null, 1)));
        assertTrue(violatedPaths(request).stream().anyMatch(p -> p.contains("productId")));
    }

    @Test
    @DisplayName("Should reject a nested purchase line with a non-positive quantity")
    void shouldRejectNestedNonPositiveQuantity() {
        var request = new OrderRequest("REF-1", PaymentMethod.VISA, "c1", List.of(new PurchaseRequest(1, 0)));
        assertTrue(violatedPaths(request).stream().anyMatch(p -> p.contains("quantity")));
    }

    @Test
    @DisplayName("Should report a violation for every invalid constraint on every field")
    void shouldReportAllInvalidTopLevelFields() {
        // reference (@NotBlank), paymentMethod (@NotNull), customerId (@NotNull/@NotEmpty/@NotBlank),
        // products (@NotEmpty) => 6 violations in total.
        var request = new OrderRequest(null, null, null, null);
        assertEquals(6, validator.validate(request).size());
    }
}
