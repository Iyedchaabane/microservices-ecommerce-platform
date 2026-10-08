package com.ichaabane.ecommerce.notification;

import com.ichaabane.ecommerce.kafka.order.Customer;
import com.ichaabane.ecommerce.kafka.order.OrderConfirmation;
import com.ichaabane.ecommerce.kafka.payment.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Notification model Unit Tests")
class NotificationModelTest {

    @Nested
    @DisplayName("Notification document")
    class NotificationTests {

        @Test
        @DisplayName("Should build a notification with its payload")
        void shouldBuild() {
            var confirmation = new OrderConfirmation("REF-1", BigDecimal.TEN, PaymentMethod.VISA,
                    new Customer("c1", "John", "Doe", "john@doe.com"), List.of());
            var date = LocalDateTime.of(2026, 10, 5, 12, 0);

            var notification = Notification.builder()
                    .id("n1").type(NotificationType.ORDER_CONFIRMATION)
                    .notificationDate(date).orderConfirmation(confirmation)
                    .build();

            assertEquals("n1", notification.getId());
            assertEquals(NotificationType.ORDER_CONFIRMATION, notification.getType());
            assertEquals(date, notification.getNotificationDate());
            assertSame(confirmation, notification.getOrderConfirmation());
        }

        @Test
        @DisplayName("Should default to nulls and allow mutation")
        void shouldAllowMutation() {
            var notification = new Notification();

            assertNull(notification.getId());
            notification.setType(NotificationType.PAYMENT_CONFIRMATION);
            assertEquals(NotificationType.PAYMENT_CONFIRMATION, notification.getType());
        }
    }

    @Nested
    @DisplayName("NotificationType enum")
    class NotificationTypeTests {

        @Test
        @DisplayName("Should expose the two notification types")
        void shouldExposeTypes() {
            assertEquals(2, NotificationType.values().length);
            assertTrue(List.of(NotificationType.values()).contains(NotificationType.ORDER_CONFIRMATION));
        }

        @Test
        @DisplayName("Should resolve a type by name")
        void shouldResolveByName() {
            assertEquals(NotificationType.PAYMENT_CONFIRMATION, NotificationType.valueOf("PAYMENT_CONFIRMATION"));
        }
    }
}
