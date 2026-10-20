package com.ichaabane.ecommerce.payment.service;

import com.ichaabane.ecommerce.notification.NotificationProducer;
import com.ichaabane.ecommerce.notification.PaymentNotificationRequest;
import com.ichaabane.ecommerce.payment.dto.PaymentRequest;
import com.ichaabane.ecommerce.payment.mapper.PaymentMapper;
import com.ichaabane.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

  private final PaymentRepository repository;
  private final PaymentMapper mapper;
  private final NotificationProducer notificationProducer;

  /**
   * Persists the payment and schedules the customer notification for AFTER the
   * transaction commits, mirroring {@code OrderService.publishAfterCommit}:
   * if the transaction rolls back, no confirmation email is sent for a payment
   * that does not exist.
   *
   * <p>Idempotency: order-service retries a checkout whose later steps failed
   * after the payment was already accepted (the payment service commits in its
   * own transaction, which the order-side rollback cannot undo). A retry with
   * the same {@code orderReference} therefore returns the existing payment id
   * instead of recording a second payment for the same order. The unique
   * constraint on {@code payment.order_reference} backstops the concurrent
   * insert that slips past the {@code findByOrderReference} check; it surfaces
   * as a 409 through {@code GlobalExceptionHandler}.</p>
   */
  @Transactional
  public Integer createPayment(PaymentRequest request) {
    // Fast path for an idempotent retry: no second row, no duplicate event.
    var existing = this.repository.findByOrderReference(request.orderReference());
    if (existing.isPresent()) {
      log.info("Payment already exists for order reference < {} > — returning the existing payment, no duplicate record",
              request.orderReference());
      return existing.get().getId();
    }

    var payment = this.repository.saveAndFlush(this.mapper.toPayment(request));

    // saveAndFlush above is what guarantees uniqueness/constraint conflicts are
    // raised here, inside the transaction, before the notification is scheduled.
    publishAfterCommit(
            new PaymentNotificationRequest(
                    request.orderReference(),
                    request.amount(),
                    request.paymentMethod(),
                    request.customer().firstname(),
                    request.customer().lastname(),
                    request.customer().email()
            )
    );
    return payment.getId();
  }

  /**
   * Sends the notification only once the DB transaction has committed, so a
   * rollback can never leave the customer with a confirmation for a payment
   * that does not exist (same contract as the order service).
   */
  private void publishAfterCommit(PaymentNotificationRequest notification) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          sendNotificationSafely(notification);
        }
      });
    } else {
      sendNotificationSafely(notification);
    }
  }

  /**
   * The transaction has already committed, so a publish failure must never
   * turn into an exception response: the caller would treat the checkout as
   * failed although the payment is recorded (and a blind retry would either be
   * rejected as a duplicate or, worse, trigger a second charge upstream).
   * Producer-level retries (Kafka {@code retries}/{@code delivery.timeout.ms})
   * already cover transient broker issues; anything left is surfaced loudly in
   * the logs — this is an at-most-once notification, not a guaranteed one.
   */
  private void sendNotificationSafely(PaymentNotificationRequest notification) {
    try {
      this.notificationProducer.sendNotification(notification);
    } catch (Exception exp) {
      log.error("Failed to publish payment notification for order reference < {} >: the payment is recorded but the customer may not be notified",
              notification.orderReference(), exp);
    }
  }
}
