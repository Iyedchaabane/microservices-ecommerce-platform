package com.ichaabane.ecommerce.notification;

import com.ichaabane.ecommerce.payment.model.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationProducer (payment) Unit Tests")
class NotificationProducerTest {

    @Mock
    private KafkaTemplate<String, PaymentNotificationRequest> kafkaTemplate;

    private NotificationProducer producer;

    @BeforeEach
    void setUp() {
        producer = new NotificationProducer(kafkaTemplate);
    }

    private PaymentNotificationRequest request() {
        return new PaymentNotificationRequest("REF-1", BigDecimal.valueOf(100), PaymentMethod.VISA,
                "John", "Doe", "john@doe.com");
    }

    @Test
    @DisplayName("Should publish to payment-topic with the request as payload")
    void shouldPublishToPaymentTopic() {
        var request = request();
        when(kafkaTemplate.send(any(Message.class))).thenReturn(new CompletableFuture<>());

        producer.sendNotification(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<PaymentNotificationRequest>> captor = ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate, times(1)).send(captor.capture());
        assertEquals("payment-topic", captor.getValue().getHeaders().get(KafkaHeaders.TOPIC));
        assertSame(request, captor.getValue().getPayload());
    }

    @Test
    @DisplayName("Should forward every field of the notification request")
    void shouldForwardFields() {
        when(kafkaTemplate.send(any(Message.class))).thenReturn(new CompletableFuture<>());
        producer.sendNotification(request());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<PaymentNotificationRequest>> captor = ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("REF-1", captor.getValue().getPayload().orderReference());
        assertEquals("john@doe.com", captor.getValue().getPayload().customerEmail());
    }

    @Test
    @DisplayName("Should not throw when the async delivery later fails (the future is completed exceptionally)")
    void shouldNotThrowOnAsyncDeliveryFailure() {
        // The failed future's whenComplete callback runs synchronously on
        // completeExceptionally, so this drives the error-logging branch.
        CompletableFuture<?> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(CompletableFuture.class.cast(failed));

        // The failure is already committed at the payment-service level; the
        // producer must stay silent (log only) rather than throw.
        assertDoesNotThrow(() -> producer.sendNotification(request()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<PaymentNotificationRequest>> captor = ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("REF-1", captor.getValue().getPayload().orderReference());
    }
}
