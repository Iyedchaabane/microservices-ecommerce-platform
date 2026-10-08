package com.ichaabane.ecommerce.order.model;

import com.ichaabane.ecommerce.orderline.model.OrderLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Order model Unit Tests")
class OrderModelTest {

    @Nested
    @DisplayName("Order entity")
    class OrderTests {

        @Test
        @DisplayName("Should build an order with the builder")
        void shouldBuild() {
            var order = Order.builder()
                    .id(7).reference("REF-1").totalAmount(BigDecimal.TEN)
                    .paymentMethod(PaymentMethod.VISA).customerId("c1")
                    .build();

            assertEquals(7, order.getId());
            assertEquals("REF-1", order.getReference());
            assertEquals(BigDecimal.TEN, order.getTotalAmount());
            assertEquals(PaymentMethod.VISA, order.getPaymentMethod());
            assertEquals("c1", order.getCustomerId());
        }

        @Test
        @DisplayName("Should expose the order lines collection")
        void shouldExposeOrderLines() {
            var line = OrderLine.builder().id(1).productId(21).quantity(2).build();
            var order = Order.builder().id(7).reference("REF-1").orderLines(List.of(line)).build();

            assertEquals(1, order.getOrderLines().size());
            assertSame(line, order.getOrderLines().get(0));
        }

        @Test
        @DisplayName("Should use the all-args constructor and setters")
        void shouldUseAllArgsAndSetters() {
            var order = new Order(1, "REF-1", BigDecimal.ONE, PaymentMethod.BITCOIN, "c1", List.of(), null, null);

            assertEquals(1, order.getId());
            order.setReference("REF-2");
            assertEquals("REF-2", order.getReference());
        }

        @Test
        @DisplayName("Should default to nulls")
        void shouldDefaultToNulls() {
            assertNull(new Order().getReference());
        }
    }

    @Nested
    @DisplayName("OrderLine entity")
    class OrderLineTests {

        @Test
        @DisplayName("Should build an order line with the builder")
        void shouldBuild() {
            var line = OrderLine.builder()
                    .id(5).order(Order.builder().id(7).build()).productId(21).quantity(3)
                    .build();

            assertEquals(5, line.getId());
            assertEquals(7, line.getOrder().getId());
            assertEquals(21, line.getProductId());
            assertEquals(3, line.getQuantity());
        }

        @Test
        @DisplayName("Should allow mutation through setters")
        void shouldAllowSetters() {
            var line = new OrderLine();
            line.setProductId(9);
            line.setQuantity(4);

            assertEquals(9, line.getProductId());
            assertEquals(4, line.getQuantity());
        }
    }

    @Nested
    @DisplayName("PaymentMethod enum")
    class PaymentMethodTests {

        @Test
        @DisplayName("Should expose every supported payment method")
        void shouldExposeAllMethods() {
            assertEquals(5, PaymentMethod.values().length);
            assertTrue(List.of(PaymentMethod.values()).contains(PaymentMethod.CREDIT_CARD));
        }

        @Test
        @DisplayName("Should resolve a method by name")
        void shouldResolveByName() {
            assertEquals(PaymentMethod.PAYPAL, PaymentMethod.valueOf("PAYPAL"));
        }
    }
}
