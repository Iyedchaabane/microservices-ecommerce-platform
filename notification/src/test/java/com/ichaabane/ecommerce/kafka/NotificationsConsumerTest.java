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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    private Message<PaymentConfirmation> paymentMessage(PaymentConfirmation payload, Object... headerAndValue) {
        var builder = MessageBuilder.withPayload(payload);
        for (int i = 0; i + 1 < headerAndValue.length; i += 2) {
            builder.setHeader((String) headerAndValue[i], headerAndValue[i + 1]);
        }
        return builder.build();
    }

    private Message<OrderConfirmation> orderMessage(OrderConfirmation payload, Object... headerAndValue) {
        var builder = MessageBuilder.withPayload(payload);
        for (int i = 0; i + 1 < headerAndValue.length; i += 2) {
            builder.setHeader((String) headerAndValue[i], headerAndValue[i + 1]);
        }
        return builder.build();
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
            consumer.consumePaymentSuccessNotifications(confirmation(), paymentMessage(confirmation()));

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

            consumer.consumePaymentSuccessNotifications(confirmation, paymentMessage(confirmation));

            var captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertEquals(confirmation, captor.getValue().getPaymentConfirmation());
        }

        @Test
        @DisplayName("Should send the payment email with the concatenated customer name")
        void shouldSendEmail() throws MessagingException {
            consumer.consumePaymentSuccessNotifications(confirmation(), paymentMessage(confirmation()));

            verify(emailService).sendPaymentSuccessEmail(
                    eq("john@doe.com"), eq("John Doe"), eq(BigDecimal.valueOf(100)), eq("REF-1"));
        }

        @Test
        @DisplayName("Should not send an email when the repository fails")
        void shouldNotEmailWhenPersistenceFails() throws MessagingException {
            when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("db down"));

            assertThrows(RuntimeException.class, () ->
                    consumer.consumePaymentSuccessNotifications(confirmation(), paymentMessage(confirmation())));

            verify(emailService, never()).sendPaymentSuccessEmail(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should propagate a mail failure")
        void shouldPropagateMailFailure() throws MessagingException {
            doThrow(new MessagingException("smtp down")).when(emailService)
                    .sendPaymentSuccessEmail(any(), any(), any(), any());

            assertThrows(MessagingException.class, () ->
                    consumer.consumePaymentSuccessNotifications(confirmation(), paymentMessage(confirmation())));
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
            consumer.consumeOrderConfirmationNotifications(confirmation(), orderMessage(confirmation()));

            var captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertEquals(NotificationType.ORDER_CONFIRMATION, captor.getValue().getType());
            assertNotNull(captor.getValue().getNotificationDate());
        }

        @Test
        @DisplayName("Should store the received order payload")
        void shouldStorePayload() throws MessagingException {
            var confirmation = confirmation();

            consumer.consumeOrderConfirmationNotifications(confirmation, orderMessage(confirmation));

            var captor = ArgumentCaptor.forClass(Notification.class);
            verify(repository).save(captor.capture());
            assertEquals(confirmation, captor.getValue().getOrderConfirmation());
        }

        @Test
        @DisplayName("Should send the order email with the concatenated customer name and products")
        void shouldSendEmail() throws MessagingException {
            var confirmation = confirmation();

            consumer.consumeOrderConfirmationNotifications(confirmation, orderMessage(confirmation));

            verify(emailService).sendOrderConfirmationEmail(
                    eq("jane@doe.com"), eq("Jane Smith"), eq(BigDecimal.valueOf(250)), eq("REF-2"),
                    eq(confirmation.products()));
        }

        @Test
        @DisplayName("Should not send an email when the repository fails")
        void shouldNotEmailWhenPersistenceFails() throws MessagingException {
            when(repository.save(any(Notification.class))).thenThrow(new RuntimeException("db down"));

            assertThrows(RuntimeException.class, () ->
                    consumer.consumeOrderConfirmationNotifications(confirmation(), orderMessage(confirmation())));

            verify(emailService, never()).sendOrderConfirmationEmail(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should propagate a mail failure")
        void shouldPropagateMailFailure() throws MessagingException {
            doThrow(new MessagingException("smtp down")).when(emailService)
                    .sendOrderConfirmationEmail(any(), any(), any(), any(), any());

            assertThrows(MessagingException.class, () ->
                    consumer.consumeOrderConfirmationNotifications(confirmation(), orderMessage(confirmation())));
        }
    }

    @Nested
    @DisplayName("Correlated logging (MDC)")
    class CorrelationPropagation {

        private PaymentConfirmation confirmation() {
            return new PaymentConfirmation("REF-3", BigDecimal.valueOf(10), PaymentMethod.VISA,
                    "John", "Doe", "john@doe.com");
        }

        @Test
        @DisplayName("A string correlation header is registered in the MDC for the message processing")
        void shouldRegisterCorrelationIdInMdc() throws MessagingException {
            assertNull(MDC.get("correlationId"), "precondition: MDC is empty");

            consumer.consumePaymentSuccessNotifications(confirmation(),
                    paymentMessage(confirmation(), "correlationId", "correlation-789"));

            // The MDC entry is removed again in finally after processing.
            assertNull(MDC.get("correlationId"), "MDC must not leak to the next message");

            // While processing, save() saw the MDC populated: prove via a captor-time snapshot.
            var mdcAtSave = new Object[] {null};
            org.mockito.Mockito.when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
                mdcAtSave[0] = MDC.get("correlationId");
                return null;
            });
            consumer.consumeOrderConfirmationNotifications(
                    new OrderConfirmation("REF-4", BigDecimal.valueOf(10), PaymentMethod.VISA,
                            new com.ichaabane.ecommerce.kafka.order.Customer("c1", "Jane", "Smith", "jane@doe.com"),
                            List.of()),
                    orderMessage(new OrderConfirmation("REF-4", BigDecimal.valueOf(10), PaymentMethod.VISA,
                            new com.ichaabane.ecommerce.kafka.order.Customer("c1", "Jane", "Smith", "jane@doe.com"),
                            List.of()), "correlationId", "correlation-789"));

            assertEquals("correlation-789", mdcAtSave[0], "the listener must log processed messages with the correlation id in the MDC");
        }

        @Test
        @DisplayName("A raw byte[] correlation header (no spring_json_header_types) is decoded instead of blowing up")
        void shouldDecodeRawByteHeader() throws MessagingException {
            var mdcAtSave = new Object[] {null};
            when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
                mdcAtSave[0] = MDC.get("correlationId");
                return null;
            });

            consumer.consumePaymentSuccessNotifications(confirmation(),
                    paymentMessage(confirmation(), "correlationId",
                            "correlation-789".getBytes(StandardCharsets.UTF_8)));

            assertEquals("correlation-789", mdcAtSave[0],
                    "the byte[] header shape must still end up as a usable log correlation id");
        }

        @Test
        @DisplayName("A malformed correlation header is dropped, never echoed into the MDC")
        void shouldDropMalformedCorrelationHeader() throws MessagingException {
            var mdcAtSave = new Object[] {null};
            when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
                mdcAtSave[0] = MDC.get("correlationId");
                return null;
            });

            consumer.consumePaymentSuccessNotifications(confirmation(),
                    paymentMessage(confirmation(), "correlationId", "bad\tid\ninjected"));

            assertNull(mdcAtSave[0], "an unsafe header value must not reach the log context");
        }

        @Test
        @DisplayName("A message without a correlation header leaves the MDC untouched")
        void shouldLeaveMdcUntouchedWithoutHeader() throws MessagingException {
            var mdcAtSave = new Object[] {"untouched"};
            when(repository.save(any(Notification.class))).thenAnswer(invocation -> null);

            consumer.consumePaymentSuccessNotifications(confirmation(), paymentMessage(confirmation()));

            assertNull(mdcAtSave[0] == null ? null : MDC.get("correlationId"));
            assertNull(MDC.get("correlationId"));
        }

        @Test
        @DisplayName("Unparseable payloads: sanitizeCorrelationId unit behavior")
        void sanitizeBehavior() {
            assertEquals("correlation-789", NotificationsConsumer.sanitizeCorrelationId("correlation-789"));
            assertNull(NotificationsConsumer.sanitizeCorrelationId(null));
            assertNull(NotificationsConsumer.sanitizeCorrelationId("   "));
            assertNull(NotificationsConsumer.sanitizeCorrelationId("bad\tid"));
            assertNull(NotificationsConsumer.sanitizeCorrelationId("x".repeat(101)));
        }
    }
}
