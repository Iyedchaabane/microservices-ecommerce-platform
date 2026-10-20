package com.ichaabane.ecommerce.payment.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ichaabane.ecommerce.notification.NotificationProducer;
import com.ichaabane.ecommerce.notification.PaymentNotificationRequest;
import com.ichaabane.ecommerce.payment.dto.Customer;
import com.ichaabane.ecommerce.payment.dto.PaymentRequest;
import com.ichaabane.ecommerce.payment.mapper.PaymentMapper;
import com.ichaabane.ecommerce.payment.model.Payment;
import com.ichaabane.ecommerce.payment.model.PaymentMethod;
import com.ichaabane.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository repository;
    @Mock
    private PaymentMapper mapper;
    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest request;
    private Payment payment;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer("1", "Test", "Test", "test@example.com");

        request = new PaymentRequest(
                BigDecimal.valueOf(2000),
                PaymentMethod.CREDIT_CARD,
                1,
                "REF123",
                customer
        );

        payment = new Payment();
        payment.setId(1);
        payment.setAmount(request.amount());
        payment.setOrderId(request.orderId());
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private PaymentNotificationRequest expectedNotification() {
        return new PaymentNotificationRequest(
                "REF123",
                BigDecimal.valueOf(2000),
                PaymentMethod.CREDIT_CARD,
                "Test",
                "Test",
                "test@example.com"
        );
    }

    @Nested
    @DisplayName("createPayment() method")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should create payment and send notification built from the request")
        void shouldCreatePaymentSuccessfully() {
            when(repository.findByOrderReference("REF123")).thenReturn(Optional.empty());
            when(mapper.toPayment(request)).thenReturn(payment);
            when(repository.saveAndFlush(payment)).thenReturn(payment);

            Integer paymentId = paymentService.createPayment(request);

            assertNotNull(paymentId);
            assertEquals(1, paymentId);

            verify(mapper, times(1)).toPayment(request);
            verify(repository, times(1)).saveAndFlush(payment);

            ArgumentCaptor<PaymentNotificationRequest> captor =
                    ArgumentCaptor.forClass(PaymentNotificationRequest.class);
            verify(notificationProducer, times(1)).sendNotification(captor.capture());
            assertEquals("REF123", captor.getValue().orderReference());
            assertEquals(BigDecimal.valueOf(2000), captor.getValue().amount());
            assertEquals(PaymentMethod.CREDIT_CARD, captor.getValue().paymentMethod());
            assertEquals("test@example.com", captor.getValue().customerEmail());
        }

        @Test
        @DisplayName("Should map the full customer name and email into the notification")
        void shouldMapCustomerIntoNotification() {
            when(repository.findByOrderReference(any())).thenReturn(Optional.empty());
            when(mapper.toPayment(any())).thenReturn(payment);
            when(repository.saveAndFlush(payment)).thenReturn(payment);

            paymentService.createPayment(request);

            var captor = ArgumentCaptor.forClass(PaymentNotificationRequest.class);
            verify(notificationProducer).sendNotification(captor.capture());
            assertEquals("Test", captor.getValue().customerFirstname());
            assertEquals("Test", captor.getValue().customerLastname());
            assertEquals("test@example.com", captor.getValue().customerEmail());
        }

        @Test
        @DisplayName("Should not send notification when the repository fails")
        void shouldNotNotifyWhenRepositoryFails() {
            when(repository.findByOrderReference("REF123")).thenReturn(Optional.empty());
            when(mapper.toPayment(request)).thenReturn(payment);
            when(repository.saveAndFlush(payment)).thenThrow(new RuntimeException("DB unavailable"));

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> paymentService.createPayment(request));

            assertEquals("DB unavailable", exception.getMessage());
            verify(mapper, times(1)).toPayment(request);
            verify(repository, times(1)).saveAndFlush(payment);
            verify(notificationProducer, never()).sendNotification(any(PaymentNotificationRequest.class));
        }

        @Test
        @DisplayName("Should not send notification or call repository when mapper fails")
        void shouldNotNotifyWhenMapperFails() {
            when(repository.findByOrderReference("REF123")).thenReturn(Optional.empty());
            when(mapper.toPayment(request)).thenThrow(new IllegalArgumentException("Invalid mapping"));

            assertThrows(IllegalArgumentException.class, () -> paymentService.createPayment(request));

            verify(mapper, times(1)).toPayment(request);
            verify(repository, never()).saveAndFlush(any());
            verify(notificationProducer, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should return the existing payment id and not create a second payment for a retried checkout")
        void shouldReturnExistingPaymentOnRetry() {
            when(repository.findByOrderReference("REF123")).thenReturn(Optional.of(payment));

            Integer paymentId = paymentService.createPayment(request);

            assertEquals(1, paymentId);
            verifyNoInteractions(mapper);
            verify(repository, never()).saveAndFlush(any());
            verify(notificationProducer, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should not propagate notification failure when no transaction is active")
        void shouldNotPropagateNotificationFailureWhenNoTransactionActive() {
            when(repository.findByOrderReference("REF123")).thenReturn(Optional.empty());
            when(mapper.toPayment(request)).thenReturn(payment);
            when(repository.saveAndFlush(payment)).thenReturn(payment);
            doThrow(new RuntimeException("Kafka down"))
                    .when(notificationProducer).sendNotification(any(PaymentNotificationRequest.class));

            Integer paymentId = assertDoesNotThrow(() -> paymentService.createPayment(request));

            assertEquals(1, paymentId);
            verify(repository, times(1)).saveAndFlush(payment);
            verify(notificationProducer, times(1)).sendNotification(any(PaymentNotificationRequest.class));
        }
    }

    @Nested
    @DisplayName("publishAfterCommit() behaviour")
    class PublishAfterCommitTests {

        /** Activates a mock transaction synchronization for the unit under test. */
        private void activateSynchronization() {
            TransactionSynchronizationManager.initSynchronization();
        }

        private List<TransactionSynchronization> registeredSynchronizations() {
            return TransactionSynchronizationManager.getSynchronizations();
        }

        @Test
        @DisplayName("Should defer the notification until the transaction commits")
        void shouldDeferNotificationUntilCommit() {
            activateSynchronization();
            when(repository.findByOrderReference(any())).thenReturn(Optional.empty());
            when(mapper.toPayment(any())).thenReturn(payment);
            when(repository.saveAndFlush(payment)).thenReturn(payment);

            paymentService.createPayment(request);

            // Inside the (still open) transaction: nothing must have been sent.
            verify(notificationProducer, never()).sendNotification(any());

            // Simulate the commit: afterCommit callbacks run synchronously.
            registeredSynchronizations().forEach(TransactionSynchronization::afterCommit);

            ArgumentCaptor<PaymentNotificationRequest> captor =
                    ArgumentCaptor.forClass(PaymentNotificationRequest.class);
            verify(notificationProducer, times(1)).sendNotification(captor.capture());
            assertEquals(expectedNotification(), captor.getValue());
        }

        @Test
        @DisplayName("Should never publish the notification when the transaction rolls back")
        void shouldNotPublishOnRollback() {
            activateSynchronization();
            when(repository.findByOrderReference(any())).thenReturn(Optional.empty());
            when(mapper.toPayment(any())).thenReturn(payment);
            when(repository.saveAndFlush(payment)).thenReturn(payment);

            paymentService.createPayment(request);

            // Simulate the rollback: only the completion callback with a rolled-back status fires;
            // afterCommit is never invoked.
            registeredSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(notificationProducer, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should not propagate a notification failure (the transaction already committed)")
        void shouldContainNotificationFailureAfterCommit() {
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            var logger = (Logger) LoggerFactory.getLogger(PaymentService.class);
            logger.addAppender(appender);

            activateSynchronization();
            when(repository.findByOrderReference(any())).thenReturn(Optional.empty());
            when(mapper.toPayment(any())).thenReturn(payment);
            when(repository.saveAndFlush(payment)).thenReturn(payment);
            doThrow(new RuntimeException("broker down"))
                    .when(notificationProducer).sendNotification(any(PaymentNotificationRequest.class));

            try {
                assertDoesNotThrow(() -> paymentService.createPayment(request));

                registeredSynchronizations().forEach(TransactionSynchronization::afterCommit);

                verify(repository).saveAndFlush(payment);
                var errors = appender.list.stream()
                        .filter(e -> e.getLevel() == Level.ERROR)
                        .toList();
                assertTrue(errors.stream().anyMatch(e -> e.getFormattedMessage()
                                .contains("Failed to publish payment notification for order reference")),
                        "the post-commit publish failure must be logged as an error");
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }
        }
    }
}
