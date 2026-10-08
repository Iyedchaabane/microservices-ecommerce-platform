package com.ichaabane.ecommerce.kafka;

import com.ichaabane.ecommerce.email.EmailService;
import com.ichaabane.ecommerce.kafka.order.OrderConfirmation;
import com.ichaabane.ecommerce.kafka.order.Product;
import com.ichaabane.ecommerce.kafka.payment.PaymentConfirmation;
import com.ichaabane.ecommerce.kafka.payment.PaymentMethod;
import com.ichaabane.ecommerce.notification.Notification;
import com.ichaabane.ecommerce.notification.NotificationRepository;
import com.ichaabane.ecommerce.notification.NotificationType;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationsConsumer Unit Tests")
class NotificationsConsumerTest {

    @Mock
    private NotificationRepository repository;
    @Mock
    private EmailService emailService;

    private NotificationsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationsConsumer(repository, emailService);
    }

    @Nested
    @DisplayName("consumePaymentSuccessNotifications()")
    class PaymentConsumer {

        private PaymentConfirmation confirmation() {
            return new PaymentConfirmation("REF-1", BigDecimal.valueOf(100), PaymentMethod.VISA,
                    "John", "Doe", "john@doe.com");
        }

        @Test
        @DisplayName("Should persist a PAYMENT_CONFIRMATION notification")
        void shouldPersistNotification() throws MessagingException {
            consumer.consumePaymentSuccessNotifications(confirmation());

            var captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertEquals(NotificationType.PAYMENT_CONFIRMATION, captor.getValue().getType());
            assertNotNull(captor.getValue().getNotificationDate());
            assertNotNull(captor.getValue().getPaymentConfirmation());
        }

        @Test
        @DisplayName("Should store the received confirmation payload")
        void shouldStorePayload() throws MessagingException {
            var confirmation = confirmation();

            consumer.consumePaymentSuccessNotifications(confirmation);

            var captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertEquals(confirmation, captor.getValue().getPaymentConfirmation());
        }

        @Test
        @DisplayName("Should send the payment email with the concatenated customer name")
        void shouldSendEmail() throws MessagingException {
            consumer.consumePaymentSuccessNotifications(confirmation());

            verify(emailService).sendPaymentSuccessEmail(
                    eq("john@doe.com"), eq("John Doe"), eq(BigDecimal.valueOf(100)), eq("REF-1"));
        }

        @Test
        @DisplayName("Should not send an email when the repository fails")
        void shouldNotEmailWhenPersistenceFails() throws MessagingException {
            when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("db down"));

            assertThrows(RuntimeException.class, () -> consumer.consumePaymentSuccessNotifications(confirmation()));

            verify(emailService, never()).sendPaymentSuccessEmail(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should propagate a mail failure")
        void shouldPropagateMailFailure() throws MessagingException {
            doThrow(new MessagingException("smtp down")).when(emailService)
                    .sendPaymentSuccessEmail(any(), any(), any(), any());

            assertThrows(MessagingException.class,
                    () -> consumer.consumePaymentSuccessNotifications(confirmation()));
        }
    }

    @Nested
    @DisplayName("consumeOrderConfirmationNotifications()")
    class OrderConsumer {

        private OrderConfirmation confirmation() {
            return new OrderConfirmation("REF-2", BigDecimal.valueOf(250), PaymentMethod.CREDIT_CARD,
                    new com.ichaabane.ecommerce.kafka.order.Customer("c1", "Jane", "Smith", "jane@doe.com"),
                    List.of(new Product(21, "Book", "A book", BigDecimal.valueOf(125), 2)));
        }

        @Test
        @DisplayName("Should persist an ORDER_CONFIRMATION notification")
        void shouldPersistNotification() throws MessagingException {
            consumer.consumeOrderConfirmationNotifications(confirmation());

            var captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertEquals(NotificationType.ORDER_CONFIRMATION, captor.getValue().getType());
            assertNotNull(captor.getValue().getNotificationDate());
        }

        @Test
        @DisplayName("Should store the received order payload")
        void shouldStorePayload() throws MessagingException {
            var confirmation = confirmation();

            consumer.consumeOrderConfirmationNotifications(confirmation);

            var captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertEquals(confirmation, captor.getValue().getOrderConfirmation());
        }

        @Test
        @DisplayName("Should send the order email with the concatenated customer name and products")
        void shouldSendEmail() throws MessagingException {
            var confirmation = confirmation();

            consumer.consumeOrderConfirmationNotifications(confirmation);

            verify(emailService).sendOrderConfirmationEmail(
                    eq("jane@doe.com"), eq("Jane Smith"), eq(BigDecimal.valueOf(250)), eq("REF-2"),
                    eq(confirmation.products()));
        }

        @Test
        @DisplayName("Should not send an email when the repository fails")
        void shouldNotEmailWhenPersistenceFails() throws MessagingException {
            when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("db down"));

            assertThrows(RuntimeException.class,
                    () -> consumer.consumeOrderConfirmationNotifications(confirmation()));

            verify(emailService, never()).sendOrderConfirmationEmail(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should propagate a mail failure")
        void shouldPropagateMailFailure() throws MessagingException {
            doThrow(new MessagingException("smtp down")).when(emailService)
                    .sendOrderConfirmationEmail(any(), any(), any(), any(), any());

            assertThrows(MessagingException.class,
                    () -> consumer.consumeOrderConfirmationNotifications(confirmation()));
        }
    }
}
