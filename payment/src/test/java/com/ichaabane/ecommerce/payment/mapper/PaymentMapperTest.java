package com.ichaabane.ecommerce.payment.mapper;

import com.ichaabane.ecommerce.payment.dto.PaymentRequest;
import com.ichaabane.ecommerce.payment.model.Payment;
import com.ichaabane.ecommerce.payment.model.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("PaymentMapper Unit Tests")
class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapper();

    @Test
    @DisplayName("Should map the request fields to the entity")
    void shouldMapRequestFields() {
        var request = new PaymentRequest(
                BigDecimal.valueOf(150),
                PaymentMethod.PAYPAL,
                42,
                "REF-42",
                null
        );

        Payment payment = mapper.toPayment(request);

        assertEquals(BigDecimal.valueOf(150), payment.getAmount());
        assertEquals(PaymentMethod.PAYPAL, payment.getPaymentMethod());
        assertEquals(42, payment.getOrderId());
    }

    @Test
    @DisplayName("Should never assign an identifier coming from the request")
    void shouldNotAssignIdentifier() {
        var request = new PaymentRequest(BigDecimal.ONE, PaymentMethod.VISA, 1, "REF-1", null);

        assertNull(mapper.toPayment(request).getId());
    }

    @Test
    @DisplayName("Should return null for a null request")
    void shouldReturnNullForNullRequest() {
        assertNull(mapper.toPayment(null));
    }
}