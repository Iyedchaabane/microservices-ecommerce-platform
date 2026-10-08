package com.ichaabane.ecommerce.kafka;

import com.ichaabane.ecommerce.kafka.order.Customer;
import com.ichaabane.ecommerce.kafka.order.OrderConfirmation;
import com.ichaabane.ecommerce.kafka.order.Product;
import com.ichaabane.ecommerce.kafka.payment.PaymentConfirmation;
import com.ichaabane.ecommerce.kafka.payment.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Kafka event model Unit Tests")
class KafkaModelsTest {

    @Test
    @DisplayName("Should expose every field of the order confirmation")
    void shouldExposeOrderConfirmation() {
        var customer = new Customer("c1", "John", "Doe", "john@doe.com");
        var product = new Product(21, "Book", "A book", BigDecimal.valueOf(50), 2);
        var confirmation = new OrderConfirmation("REF-1", BigDecimal.valueOf(100), PaymentMethod.VISA,
                customer, List.of(product));

        assertEquals("REF-1", confirmation.orderReference());
        assertEquals(BigDecimal.valueOf(100), confirmation.totalAmount());
        assertEquals(PaymentMethod.VISA, confirmation.paymentMethod());
        assertEquals(customer, confirmation.customer());
        assertEquals(1, confirmation.products().size());
    }

    @Test
    @DisplayName("Should expose every field of the payment confirmation")
    void shouldExposePaymentConfirmation() {
        var confirmation = new PaymentConfirmation("REF-2", BigDecimal.valueOf(100), PaymentMethod.PAYPAL,
                "Jane", "Smith", "jane@doe.com");

        assertEquals("REF-2", confirmation.orderReference());
        assertEquals(BigDecimal.valueOf(100), confirmation.amount());
        assertEquals(PaymentMethod.PAYPAL, confirmation.paymentMethod());
        assertEquals("Jane", confirmation.customerFirstname());
        assertEquals("Smith", confirmation.customerLastname());
        assertEquals("jane@doe.com", confirmation.customerEmail());
    }

    @Test
    @DisplayName("Should expose every field of the customer record")
    void shouldExposeCustomer() {
        var customer = new Customer("c1", "John", "Doe", "john@doe.com");

        assertEquals("c1", customer.id());
        assertEquals("John", customer.firstname());
        assertEquals("Doe", customer.lastname());
        assertEquals("john@doe.com", customer.email());
    }

    @Test
    @DisplayName("Should expose every field of the product record")
    void shouldExposeProduct() {
        var product = new Product(21, "Book", "A book", BigDecimal.valueOf(50), 2);

        assertEquals(21, product.productId());
        assertEquals("Book", product.name());
        assertEquals("A book", product.description());
        assertEquals(BigDecimal.valueOf(50), product.price());
        assertEquals(2, product.quantity());
    }

    @Test
    @DisplayName("Should resolve every payment method of the payment module")
    void shouldExposePaymentMethods() {
        assertEquals(5, PaymentMethod.values().length);
        assertEquals(PaymentMethod.CREDIT_CARD, PaymentMethod.valueOf("CREDIT_CARD"));
    }
}
