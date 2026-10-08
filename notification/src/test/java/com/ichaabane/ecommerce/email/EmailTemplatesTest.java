package com.ichaabane.ecommerce.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("EmailTemplates Unit Tests")
class EmailTemplatesTest {

    @Test
    @DisplayName("Should resolve the order confirmation template and subject")
    void shouldExposeOrderTemplate() {
        assertEquals("order-confirmation.html", EmailTemplates.ORDER_CONFIRMATION.getTemplate());
        assertEquals("Order confirmation", EmailTemplates.ORDER_CONFIRMATION.getSubject());
    }

    @Test
    @DisplayName("Should resolve the payment confirmation template and subject")
    void shouldExposePaymentTemplate() {
        assertEquals("payment-confirmation.html", EmailTemplates.PAYMENT_CONFIRMATION.getTemplate());
        assertEquals("Payment successfully processed", EmailTemplates.PAYMENT_CONFIRMATION.getSubject());
    }

    @Test
    @DisplayName("Should define exactly two templates")
    void shouldDefineTwoTemplates() {
        assertEquals(2, EmailTemplates.values().length);
    }

    @Test
    @DisplayName("Should resolve a template by name")
    void shouldResolveByName() {
        assertEquals(EmailTemplates.ORDER_CONFIRMATION, EmailTemplates.valueOf("ORDER_CONFIRMATION"));
    }
}
