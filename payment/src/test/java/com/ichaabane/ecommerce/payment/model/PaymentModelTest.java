package com.ichaabane.ecommerce.payment.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Payment model Unit Tests")
class PaymentModelTest {

    @Nested
    @DisplayName("Payment entity")
    class PaymentTests {

        @Test
        @DisplayName("Should build a payment with the builder")
        void shouldBuild() {
            var payment = Payment.builder()
                    .id(1).amount(BigDecimal.valueOf(100)).paymentMethod(PaymentMethod.VISA)
                    .orderId(7).orderReference("REF-1")
                    .build();

            assertEquals(1, payment.getId());
            assertEquals(BigDecimal.valueOf(100), payment.getAmount());
            assertEquals(PaymentMethod.VISA, payment.getPaymentMethod());
            assertEquals(7, payment.getOrderId());
            assertEquals("REF-1", payment.getOrderReference());
        }

        @Test
        @DisplayName("Should default to nulls and allow mutation")
        void shouldAllowMutation() {
            var payment = new Payment();

            assertNull(payment.getId());
            payment.setAmount(BigDecimal.ONE);
            payment.setOrderReference("REF-2");

            assertEquals(BigDecimal.ONE, payment.getAmount());
            assertEquals("REF-2", payment.getOrderReference());
        }
    }

    @Nested
    @DisplayName("PaymentMethod enum")
    class PaymentMethodTests {

        @Test
        @DisplayName("Should expose every supported payment method")
        void shouldExposeAllMethods() {
            assertEquals(5, PaymentMethod.values().length);
            assertTrue(List.of(PaymentMethod.values()).contains(PaymentMethod.MASTER_CARD));
        }

        @Test
        @DisplayName("Should resolve a method by name")
        void shouldResolveByName() {
            assertEquals(PaymentMethod.BITCOIN, PaymentMethod.valueOf("BITCOIN"));
        }
    }
}
