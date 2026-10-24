package com.ichaabane.ecommerce.kafka;

import com.ichaabane.ecommerce.email.EmailService;
import com.ichaabane.ecommerce.kafka.order.OrderConfirmation;
import com.ichaabane.ecommerce.kafka.payment.PaymentConfirmation;
import com.ichaabane.ecommerce.notification.Notification;
import com.ichaabane.ecommerce.notification.NotificationRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import static com.ichaabane.ecommerce.notification.NotificationType.ORDER_CONFIRMATION;
import static com.ichaabane.ecommerce.notification.NotificationType.PAYMENT_CONFIRMATION;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationsConsumer {

    /**
     * Kafka record-header name of the manual correlation ID set by
     * {@code OrderProducer}/{@code NotificationProducer}. spring-kafka's default
     * {@code DefaultKafkaHeaderMapper} maps a producer message header named
     * {@code correlationId} onto the broker record header and back to the
     * consumer {@code Message} header ({@code KafkaHeaders#CORRELATION_ID} is
     * the request/reply envelope name, not this one).
     */
    static final String KAFKA_CORRELATION_ID_HEADER = "correlationId";

    /** A correlation header is a log/MDC field: only bounded, header-safe
     *  values are propagated, anything else is dropped without being echoed
     *  verbatim into a log message (log-forging prevention, review S-2). */
    static String sanitizeCorrelationId(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        return SAFE_CORRELATION_PATTERN.matcher(candidate).matches() ? candidate : null;
    }

    /** Accepts both shapes the header can surface in (see extractCorrelationId). */
    private static final Pattern SAFE_CORRELATION_PATTERN =
            Pattern.compile("[A-Za-z0-9:._/-]{1,100}");

    /**
     * Extracts the correlation header defensively. The default
     * {@code DefaultKafkaHeaderMapper} decodes string-typed headers only when
     * the record carries {@code spring_json_header_types}; producers without it
     * leave the header as raw {@code byte[]}, and
     * {@code MessageHeaders.get(name, String.class)} on that throws — this
     * accepts both shapes instead.
     */
    static String extractCorrelationId(org.springframework.messaging.MessageHeaders headers) {
        Object raw = headers.get(KAFKA_CORRELATION_ID_HEADER);
        if (raw instanceof String s) {
            return sanitizeCorrelationId(s);
        }
        if (raw instanceof byte[] bytes) {
            return sanitizeCorrelationId(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }
        return null;
    }

    private final NotificationRepository repository;
    private final EmailService emailService;

    @KafkaListener(topics = "payment-topic", groupId = "paymentGroup")
    public void consumePaymentSuccessNotifications(PaymentConfirmation paymentConfirmation,
                                                   Message<PaymentConfirmation> message) throws MessagingException {
        // Correlated logging (review R-10/S-4): the gateway-issued correlation id
        // rides the Kafka header; register it in the MDC so every log line of
        // this message's processing (and the async email it triggers) carries it.
        String incomingCorrelationId = extractCorrelationId(message.getHeaders());
        if (incomingCorrelationId != null) {
            MDC.put("correlationId", incomingCorrelationId);
        }
        try {
            log.info("Consuming the message from payment-topic Topic:: orderReference={}", paymentConfirmation.orderReference());
            repository.save(
                    Notification.builder()
                            .type(PAYMENT_CONFIRMATION)
                            .notificationDate(LocalDateTime.now())
                            .paymentConfirmation(paymentConfirmation)
                            .build()
            );
            // The async email submission stays inside the MDC scope: the executor's
            // TaskDecorator (AsyncMdcConfiguration) captures the MDC at submission
            // time, so the email logs carry the same correlationId (review R-10).
            String customerName = paymentConfirmation.customerFirstname() + " " + paymentConfirmation.customerLastname();
            emailService.sendPaymentSuccessEmail(
                    paymentConfirmation.customerEmail(),
                    customerName,
                    paymentConfirmation.amount(),
                    paymentConfirmation.orderReference()
            );
        } finally {
            MDC.remove("correlationId");
        }
    }

    @KafkaListener(topics = "order-topic", groupId = "orderGroup")
    public void consumeOrderConfirmationNotifications(OrderConfirmation orderConfirmation,
                                                      Message<OrderConfirmation> message) throws MessagingException {
        String incomingCorrelationId = extractCorrelationId(message.getHeaders());
        if (incomingCorrelationId != null) {
            MDC.put("correlationId", incomingCorrelationId);
        }
        try {
            log.info("Consuming the message from order-topic Topic:: orderReference={}", orderConfirmation.orderReference());
            repository.save(
                    Notification.builder()
                            .type(ORDER_CONFIRMATION)
                            .notificationDate(LocalDateTime.now())
                            .orderConfirmation(orderConfirmation)
                            .build()
            );
            // Same as the payment listener: submission inside the MDC scope keeps
            // the async email logs correlated (review R-10).
            String customerName = orderConfirmation.customer().firstname() + " " + orderConfirmation.customer().lastname();
            emailService.sendOrderConfirmationEmail(
                    orderConfirmation.customer().email(),
                    customerName,
                    orderConfirmation.totalAmount(),
                    orderConfirmation.orderReference(),
                    orderConfirmation.products()
            );
        } finally {
            MDC.remove("correlationId");
        }
    }
}
