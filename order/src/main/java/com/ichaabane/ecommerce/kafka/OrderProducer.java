package com.ichaabane.ecommerce.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import static org.springframework.kafka.support.KafkaHeaders.TOPIC;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProducer {

    private final KafkaTemplate<String, OrderConfirmation> kafkaTemplate;

    public void sendOrderConfirmation(OrderConfirmation orderConfirmation) {
        // PII fix (audit findings F-20/F-24, SIEM review §field inventory): the
        // customer's real name is not an operational log field — it duplicated
        // personal data into every Logback sink (console + JSON aggregates).
        log.info("Sending order confirmation to Kafka topic 'order-topic' for reference='{}', with customer id present={}",
                orderConfirmation.orderReference(),
                orderConfirmation.customer() != null && orderConfirmation.customer().firstname() != null);

        var messageBuilder = MessageBuilder
                .withPayload(orderConfirmation)
                .setHeader(TOPIC, "order-topic");

        // Correlation fix (audit finding F-21): the header must carry the
        // gateway-issued X-Correlation-Id (MDC "correlationId", set by
        // CorrelationIdFilter). The trace id from the tracing bridge is kept
        // only as a fallback for internal hops that bypass the gateway.
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("traceId");
        }
        if (correlationId != null && !correlationId.isBlank()) {
            messageBuilder.setHeader("correlationId", correlationId);
        }

        Message<OrderConfirmation> message = messageBuilder.build();

        var future = kafkaTemplate.send(message);
        if (future != null) {
            future.whenComplete((result, error) -> {
                if (error == null) {
                    log.info("Order confirmation delivered: topic='{}', partition={}, offset={}",
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("Order confirmation delivery failed for reference='{}' — Kafka retries were exhausted; downstream notification may be missing",
                            orderConfirmation.orderReference(), error);
                }
            });
        }
    }

}
