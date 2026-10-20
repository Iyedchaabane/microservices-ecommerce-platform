package com.ichaabane.ecommerce.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import static org.springframework.kafka.support.KafkaHeaders.TOPIC;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProducer {

  private final KafkaTemplate<String, PaymentNotificationRequest> kafkaTemplate;

  /**
   * {@code kafkaTemplate.send} is asynchronous: its returned future is where
   * broker-side delivery failures eventually surface (after the broker
   * exhausts its producer-level retries). Ignoring it—as before—made those
   * failures invisible, so the completion is now logged on both paths. This
   * makes the failure observable; it does not make delivery guaranteed.
   */
  public void sendNotification(PaymentNotificationRequest request) {
    log.info("Sending payment notification for order reference = < {} >", request.orderReference());
    Message<PaymentNotificationRequest> message = MessageBuilder
            .withPayload(request)
            .setHeader(TOPIC, "payment-topic")
            .build();

    this.kafkaTemplate.send(message).whenComplete((result, error) -> {
      if (error == null) {
        log.info("Payment notification delivered to topic < {} > partition < {} > offset < {} >",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
      } else {
        log.error("Payment notification delivery failed for order reference < {} > — Kafka retries were exhausted; the customer may not be notified",
                request.orderReference(), error);
      }
    });
  }
}
