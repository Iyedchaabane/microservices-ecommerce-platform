package com.ichaabane.ecommerce.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("KafkaErrorHandlingConfig")
class KafkaErrorHandlingConfigTest {

    private static final int TOTAL_ATTEMPTS = 10;

    @Test
    @DisplayName("CommonErrorHandler bean is a DefaultErrorHandler wired with a DeadLetterPublishingRecoverer")
    void errorHandlerBeanUsesDeadLetterRecoverer() {
        var props = new KafkaProperties();
        props.getConsumer().setBootstrapServers(List.of("localhost:9092"));
        var config = new KafkaErrorHandlingConfig();

        assertInstanceOf(DeadLetterPublishingRecoverer.class, config.dltRecoverer(props));

        assertInstanceOf(DefaultErrorHandler.class, config.kafkaCommonErrorHandler(props));
    }

    @Test
    @DisplayName("A retried-out record is published to '<topic>-dlt' instead of being silently dropped")
    void exhaustedRecordIsPublishedToDlt() {
        // Regression fix (audit test-execution report T-xx): the recoverer
        // publishes through KafkaOperations.send(ProducerRecord) — verified
        // against spring-kafka 3.1.4 DeadLetterPublishingRecoverer#sendOrThrow —
        // NOT through the 4-argument (topic, partition, key, value) overload.
        // Stubbing/verifying the wrong overload previously made the recoverer
        // throw an NPE inside the tracker, which SeekUtils.doSeeks swallows,
        // so the test loop never observed a recovery.
        var jsonTemplate = mock(KafkaOperations.class);
        var bytesTemplate = mock(KafkaOperations.class);
        when(jsonTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        var recoverer = new DeadLetterPublishingRecoverer(
                KafkaErrorHandlingConfig.valueTemplateResolver(jsonTemplate, bytesTemplate),
                KafkaErrorHandlingConfig.DLT_DESTINATION_RESOLVER);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 9L));

        var record = new ConsumerRecord<>("order-topic", 0, 8L, "RUNTIME-CHECK-1", "poison-value");

        exhaustRetries(handler, record, jsonTemplate);

        var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(jsonTemplate).send(recordCaptor.capture());
        var dltRecord = recordCaptor.getValue();
        assertEquals("order-topic-dlt", dltRecord.topic(), "the DLT destination must be '<topic>-dlt'");
        // With a Mockito Consumer, partitionsFor returns an empty partition
        // list, so DeadLetterPublishingRecoverer#checkPartition deliberately
        // routes to ProducerRecord.partition()==null (the real producer then
        // resolves the partition). The resolver itself is asserted separately
        // in dltDestinationMatchesTheFrameworkDefault.
        assertNull(dltRecord.partition(), "empty partition info -> producer resolves the partition");
        assertEquals("RUNTIME-CHECK-1", dltRecord.key(), "the record key must be preserved");
        assertEquals("poison-value", dltRecord.value(),
                "the record value must be preserved in the DLT publish");
        verifyNoInteractions(bytesTemplate);
    }

    @Test
    @DisplayName("Raw byte[] payloads route through the byte[] template, business objects through the JSON template")
    void payloadShapeSelectsTheTemplate() {
        // Same ProducerRecord-overload rationale as exhaustedRecordIsPublishedToDlt.
        var jsonTemplate = mock(KafkaOperations.class);
        var bytesTemplate = mock(KafkaOperations.class);
        when(bytesTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        var recoverer = new DeadLetterPublishingRecoverer(
                KafkaErrorHandlingConfig.valueTemplateResolver(jsonTemplate, bytesTemplate),
                KafkaErrorHandlingConfig.DLT_DESTINATION_RESOLVER);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(0L, 9L));

        var record = new ConsumerRecord<>("payment-topic", 0, 3L, "k", "raw".getBytes());

        exhaustRetries(handler, record, bytesTemplate);

        var recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(bytesTemplate).send(recordCaptor.capture());
        var dltRecord = recordCaptor.getValue();
        assertEquals("payment-topic-dlt", dltRecord.topic());
        assertNull(dltRecord.partition(), "empty partition info (mocked consumer) -> producer resolves the partition");
        assertEquals("k", dltRecord.key());
        assertArrayEquals("raw".getBytes(), (byte[]) dltRecord.value());
        verifyNoInteractions(jsonTemplate);
    }

    @Test
    @DisplayName("Destination resolver keeps the original partition and appends '-dlt'")
    void dltDestinationMatchesTheFrameworkDefault() {
        var destination = KafkaErrorHandlingConfig.DLT_DESTINATION_RESOLVER.apply(
                new ConsumerRecord<>("payment-topic", 2, 3L, "k", "v"), new RuntimeException("boom"));

        assertEquals(new TopicPartition("payment-topic-dlt", 2), destination);
    }

    @Test
    @DisplayName("Template resolver is a pure lookup: byte[] → bytes template, else JSON template")
    void resolverReturnsTemplatesWithoutSending() {
        var jsonTemplate = mock(KafkaOperations.class);
        var bytesTemplate = mock(KafkaOperations.class);
        var resolver = KafkaErrorHandlingConfig.valueTemplateResolver(jsonTemplate, bytesTemplate);

        assertSame(bytesTemplate, resolver.apply(new ProducerRecord<>("t", "k", "raw".getBytes())));
        assertSame(jsonTemplate, resolver.apply(new ProducerRecord<>("t", "k", new Object())));
    }

    /**
     * Drives {@link DefaultErrorHandler#handleRemaining} to exhaustion the way the container does
     * during live retries: calls 1..9 signal a seek-and-redeliver (KafkaException), the 10th call
     * has no retry left and hands the record to the recoverer.
     */
    private static void exhaustRetries(DefaultErrorHandler handler, ConsumerRecord<?, ?> record,
                                       KafkaOperations<?, ?> expectedTemplate) {
        var consumer = mock(Consumer.class);
        var container = mock(MessageListenerContainer.class);
        var exception = new MessagingExceptionForTest();

        for (int attempt = 1; attempt <= TOTAL_ATTEMPTS; attempt++) {
            try {
                handler.handleRemaining(exception, List.of(record), consumer, container);
            } catch (KafkaException retryPending) {
                continue;
            }
            // handleRemaining returned cleanly: recoverer invoked.
            @SuppressWarnings({"unchecked", "rawtypes"})
            KafkaOperations rawTemplate = expectedTemplate;
            verify(rawTemplate, times(1))
                    .send(any(ProducerRecord.class));
            return;
        }
        throw new AssertionError("error handler kept seeking after " + TOTAL_ATTEMPTS + " attempts; DLT not reached");
    }

    /** Distinct type so the tracker never treats repeated calls as an exception change. */
    private static final class MessagingExceptionForTest extends RuntimeException {
    }
}
