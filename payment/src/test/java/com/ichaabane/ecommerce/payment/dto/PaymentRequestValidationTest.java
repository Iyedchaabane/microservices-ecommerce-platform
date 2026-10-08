package com.ichaabane.ecommerce.payment.dto;

import com.ichaabane.ecommerce.payment.model.PaymentMethod;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PaymentRequest Bean Validation Tests")
class PaymentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> violatedPaths(PaymentRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private PaymentRequest validRequest() {
        return new PaymentRequest(BigDecimal.valueOf(100), PaymentMethod.CREDIT_CARD, 1, "REF-1",
                new Customer("c1", "John", "Doe", "john@doe.com"));
    }

    @Test
    @DisplayName("Should accept a fully valid request")
    void shouldAcceptValid() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null amount")
    void shouldRejectNullAmount() {
        var request = new PaymentRequest(null, PaymentMethod.VISA, 1, "REF-1",
                new Customer("c1", "John", "Doe", "john@doe.com"));
        assertTrue(violatedPaths(request).contains("amount"));
    }

    @Test
    @DisplayName("Should reject a zero amount")
    void shouldRejectZeroAmount() {
        var request = new PaymentRequest(BigDecimal.ZERO, PaymentMethod.VISA, 1, "REF-1",
                new Customer("c1", "John", "Doe", "john@doe.com"));
        assertTrue(violatedPaths(request).contains("amount"));
    }

    @Test
    @DisplayName("Should reject a negative amount")
    void shouldRejectNegativeAmount() {
        var request = new PaymentRequest(BigDecimal.valueOf(-1), PaymentMethod.VISA, 1, "REF-1",
                new Customer("c1", "John", "Doe", "john@doe.com"));
        assertTrue(violatedPaths(request).contains("amount"));
    }

    @Test
    @DisplayName("Should reject a null payment method")
    void shouldRejectNullPaymentMethod() {
        var request = new PaymentRequest(BigDecimal.TEN, null, 1, "REF-1",
                new Customer("c1", "John", "Doe", "john@doe.com"));
        assertTrue(violatedPaths(request).contains("paymentMethod"));
    }

    @Test
    @DisplayName("Should reject a null order id")
    void shouldRejectNullOrderId() {
        var request = new PaymentRequest(BigDecimal.TEN, PaymentMethod.VISA, null, "REF-1",
                new Customer("c1", "John", "Doe", "john@doe.com"));
        assertTrue(violatedPaths(request).contains("orderId"));
    }

    @Test
    @DisplayName("Should reject a null order reference")
    void shouldRejectNullOrderReference() {
        var request = new PaymentRequest(BigDecimal.TEN, PaymentMethod.VISA, 1, null,
                new Customer("c1", "John", "Doe", "john@doe.com"));
        assertTrue(violatedPaths(request).contains("orderReference"));
    }

    @Test
    @DisplayName("Should reject a blank order reference")
    void shouldRejectBlankOrderReference() {
        var request = new PaymentRequest(BigDecimal.TEN, PaymentMethod.VISA, 1, "  ",
                new Customer("c1", "John", "Doe", "john@doe.com"));
        assertTrue(violatedPaths(request).contains("orderReference"));
    }

    @Test
    @DisplayName("Should reject a null customer")
    void shouldRejectNullCustomer() {
        var request = new PaymentRequest(BigDecimal.TEN, PaymentMethod.VISA, 1, "REF-1", null);
        assertTrue(violatedPaths(request).contains("customer"));
    }

    @Test
    @DisplayName("Should reject a nested customer without a firstname")
    void shouldRejectNestedNullFirstname() {
        var request = new PaymentRequest(BigDecimal.TEN, PaymentMethod.VISA, 1, "REF-1",
                new Customer("c1", null, "Doe", "john@doe.com"));
        assertTrue(violatedPaths(request).stream().anyMatch(p -> p.contains("firstname")));
    }

    @Test
    @DisplayName("Should reject a nested customer with a blank lastname")
    void shouldRejectNestedBlankLastname() {
        var request = new PaymentRequest(BigDecimal.TEN, PaymentMethod.VISA, 1, "REF-1",
                new Customer("c1", "John", "  ", "john@doe.com"));
        assertTrue(violatedPaths(request).stream().anyMatch(p -> p.contains("lastname")));
    }

    @Test
    @DisplayName("Should reject a nested customer with an invalid email")
    void shouldRejectNestedInvalidEmail() {
        var request = new PaymentRequest(BigDecimal.TEN, PaymentMethod.VISA, 1, "REF-1",
                new Customer("c1", "John", "Doe", "not-an-email"));
        assertTrue(violatedPaths(request).stream().anyMatch(p -> p.contains("email")));
    }

    @Test
    @DisplayName("Should accept a customer without an id")
    void shouldAcceptCustomerWithoutId() {
        var request = new PaymentRequest(BigDecimal.TEN, PaymentMethod.VISA, 1, "REF-1",
                new Customer(null, "John", "Doe", "john@doe.com"));
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("Should report one violation per invalid top-level field when all are missing")
    void shouldReportAllTopLevelViolations() {
        var request = new PaymentRequest(null, null, null, null, null);
        assertEquals(5, validator.validate(request).size());
    }
}
