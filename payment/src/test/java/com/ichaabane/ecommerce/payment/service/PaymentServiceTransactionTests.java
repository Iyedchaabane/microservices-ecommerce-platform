package com.ichaabane.ecommerce.payment.service;

import com.ichaabane.ecommerce.notification.NotificationProducer;
import com.ichaabane.ecommerce.notification.PaymentNotificationRequest;
import com.ichaabane.ecommerce.payment.dto.Customer;
import com.ichaabane.ecommerce.payment.dto.PaymentRequest;
import com.ichaabane.ecommerce.payment.model.Payment;
import com.ichaabane.ecommerce.payment.model.PaymentMethod;
import com.ichaabane.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Full-stack tests for the transaction/Kafka contract (real DB, mocked
 * producer): the notification is only published after a successful commit, a
 * rollback never publishes, and a retried checkout cannot double-charge.
 *
 * <p>Named {@code *Tests} (not {@code *IT}) so Surefire's default includes
 * run it — {@code *IT} classes are silently skipped by the maven-surefire
 * plugin's {@code test} goal.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PaymentService transaction/Kafka integration tests (H2)")
class PaymentServiceTransactionTests {

    @Autowired
    private PaymentService service;

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // @MockBean (not @MockitoBean): Spring Boot 3.2 in this project predates it.
    @MockBean
    private NotificationProducer notificationProducer;

    private TransactionTemplate newTxTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private PaymentRequest request(String reference) {
        return new PaymentRequest(BigDecimal.valueOf(100), PaymentMethod.CREDIT_CARD, 1, reference,
                new Customer("c1", "John", "Doe", "john@doe.com"));
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Publishes the notification only after the transaction commits")
    void publishesOnlyAfterCommit() {
        var template = newTxTemplate();

        template.execute(status -> {
            service.createPayment(request("REF-IT-1"));
            // Still inside the (open) transaction: the producer must be untouched.
            verify(notificationProducer, never()).sendNotification(any());
            return null;
        });
        // afterCommit callbacks run synchronously before commit() returns.
        verify(notificationProducer, times(1)).sendNotification(any(PaymentNotificationRequest.class));
        assertEquals(1, repository.count(), "the payment must be committed");
    }

    @Test
    @DisplayName("Never publishes the notification when the transaction rolls back")
    void neverPublishesOnRollback() {
        var template = newTxTemplate();

        template.execute(status -> {
            service.createPayment(request("REF-IT-RB"));
            status.setRollbackOnly();
            return null;
        });

        verify(notificationProducer, never()).sendNotification(any());
        assertEquals(0, repository.count(), "the payment must be rolled back too");
    }

    @Test
    @DisplayName("A retried checkout with the same reference is idempotent: same id, one event")
    void retryIsIdempotent() {
        var template = newTxTemplate();

        Integer firstId = template.execute(t -> service.createPayment(request("REF-IT-IDEM")));
        assertEquals(1, repository.count());

        // Second checkout attempt (new transaction, same reference): must return
        // the existing payment without saving or publishing again.
        Integer secondId = template.execute(t -> service.createPayment(request("REF-IT-IDEM")));

        assertEquals(firstId, secondId);
        assertEquals(1, repository.count());
        verify(notificationProducer, times(1)).sendNotification(any(PaymentNotificationRequest.class));
    }

    @Test
    @DisplayName("The unique constraint on order_reference backstops concurrent duplicate inserts")
    void uniqueReferenceConstraint() {
        var duplicate = Payment.builder()
                .amount(BigDecimal.TEN)
                .paymentMethod(PaymentMethod.VISA)
                .orderId(2)
                .orderReference("REF-IT-UNIQ")
                .build();
        repository.saveAndFlush(duplicate);

        var raced = Payment.builder()
                .amount(BigDecimal.ONE)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .orderId(3)
                .orderReference("REF-IT-UNIQ")
                .build();

        var exception = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(raced));

        assertEquals(1, repository.count(), "the raced duplicate must be rejected by the constraint");
    }

    @Test
    @DisplayName("Never publishes the notification when an exception occurs before commit")
    void neverPublishesOnExceptionBeforeCommit() {
        var template = newTxTemplate();

        assertThrows(RuntimeException.class, () -> template.execute(status -> {
            service.createPayment(request("REF-IT-EX"));
            throw new RuntimeException("Simulated business exception before commit");
        }));

        verify(notificationProducer, never()).sendNotification(any());
        assertEquals(0, repository.count(), "the payment must not be committed");
    }

    @Test
    @DisplayName("Does not fail the transaction or rollback when Kafka publishing fails after commit")
    void doesNotFailWhenKafkaPublishingFailsAfterCommit() {
        org.mockito.Mockito.doThrow(new RuntimeException("Kafka broker down"))
                .when(notificationProducer).sendNotification(any(PaymentNotificationRequest.class));

        var template = newTxTemplate();
        Integer id = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                template.execute(status -> service.createPayment(request("REF-IT-KAFKA-FAIL"))));

        org.junit.jupiter.api.Assertions.assertNotNull(id);
        assertEquals(1, repository.count(), "the payment must still be committed");
        verify(notificationProducer, times(1)).sendNotification(any(PaymentNotificationRequest.class));
    }
}
