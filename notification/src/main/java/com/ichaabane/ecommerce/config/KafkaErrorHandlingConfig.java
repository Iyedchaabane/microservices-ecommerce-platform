package com.ichaabane.ecommerce.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Common error handling for the {@code @KafkaListener} containers (review finding R-5b /
 * "retried-out Kafka messages must not be silently dropped" in {@code log-review.md}).
 *
 * <p>Without a {@code CommonErrorHandler} bean the containers use Spring Kafka's default
 * {@code DefaultErrorHandler}: after the retry budget is exhausted the failed record is simply
 * skipped. This was observed live (review §0 runtime verification): a failing message was retried
 * 10 times and then vanished, leaving no trace other than the ERROR log lines. With a
 * {@link DeadLetterPublishingRecoverer} the exhausted record — plus diagnostics headers naming the
 * original topic/partition/offset and exception — is published to {@code <original-topic>-dlt}
 * for inspection. Skipping continues afterwards, so one poison record cannot wedge the partition.</p>
 *
 * <p>spring-kafka 3.1.x has no per-value-type template registry ({@code addTemplate} arrived
 * later), so value-shape routing is done with the constructor's {@code templateResolver}
 * {@link Function}: the failed record's payload is either a business object
 * ({@code OrderConfirmation}/{@code PaymentConfirmation}) published through the JSON template, or
 * raw {@code byte[]} from a deserialization/conversion failure below the listener, published
 * through the byte[] template (a byte[] cannot go through the JSON serializer).</p>
 *
 * <p>The retry policy mirrors the runtime behavior verified earlier ({@code FixedBackOff{interval=0,
 * maxAttempts=9}} → 10 attempts) so the change is strictly additive: same retry budget, the only
 * difference is what happens on exhaustion.</p>
 *
 * <p>Spring Boot wires a unique {@link CommonErrorHandler} bean into the default
 * {@code kafkaListenerContainerFactory} (verified against spring-boot-autoconfigure 3.2.5,
 * {@code KafkaAnnotationDrivenConfiguration}), so both listeners inherit this without a custom
 * factory.</p>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    /** Default DLT suffix used by {@link DeadLetterPublishingRecoverer}: {@code <topic>-dlt}. */
    public static final String DEFAULT_DLT_SUFFIX = "dlt";

    /** Same-partition destination as the framework default: {@code <topic>-dlt}, original partition. */
    static final BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> DLT_DESTINATION_RESOLVER =
            (record, exception) -> new TopicPartition(
                    record.topic() + "-" + DEFAULT_DLT_SUFFIX,
                    record.partition());

    @Bean
    public CommonErrorHandler kafkaCommonErrorHandler(KafkaProperties kafkaProperties) {
        // Same retry budget as the runtime-observed default: 9 retries (10 total attempts).
        return new DefaultErrorHandler(dltRecoverer(kafkaProperties), new FixedBackOff(0L, 9L));
    }

    /** The recoverer itself, extracted so unit tests can assert the DLT wiring directly. */
    DeadLetterPublishingRecoverer dltRecoverer(KafkaProperties kafkaProperties) {
        KafkaTemplate<String, Object> jsonTemplate = jsonDltTemplate(kafkaProperties);
        KafkaTemplate<String, byte[]> bytesTemplate = bytesDltTemplate(kafkaProperties);

        return new DeadLetterPublishingRecoverer(valueTemplateResolver(jsonTemplate, bytesTemplate),
                DLT_DESTINATION_RESOLVER);
    }

    /** Picks the dead-letter template by payload shape; static so unit tests can exercise it with mocks. */
    static Function<ProducerRecord<?, ?>, KafkaOperations<?, ?>> valueTemplateResolver(
            KafkaOperations<?, ?> jsonTemplate, KafkaOperations<?, ?> bytesTemplate) {
        return record -> record.value() instanceof byte[] ? bytesTemplate : jsonTemplate;
    }

    /** DLT publisher for business objects, serialized as JSON (keys stay strings). */
    KafkaTemplate<String, Object> jsonDltTemplate(KafkaProperties kafkaProperties) {
        var factory = new DefaultKafkaProducerFactory<String, Object>(
                dltProducerProps(kafkaProperties, JsonSerializer.class.getName()),
                new StringSerializer(),
                new JsonSerializer<>());
        var template = new KafkaTemplate<>(factory);
        template.setObservationEnabled(true);
        return template;
    }

    /** DLT publisher for raw-byte payloads (deserialization/conversion failures). */
    KafkaTemplate<String, byte[]> bytesDltTemplate(KafkaProperties kafkaProperties) {
        var factory = new DefaultKafkaProducerFactory<String, byte[]>(
                dltProducerProps(kafkaProperties, ByteArraySerializer.class.getName()),
                new StringSerializer(),
                new ByteArraySerializer());
        var template = new KafkaTemplate<>(factory);
        template.setObservationEnabled(true);
        return template;
    }

    /**
     * Producer properties for both DLT templates. Bootstrap list resolves like the application's
     * other Kafka clients: the config-server value on {@code spring.kafka.consumer.*} wins, then
     * the base {@code spring.kafka.*} list, then the localhost default. (Data initialization is
     * not needed: producers connect lazily on first DLT publish.)
     */
    private static Map<String, Object> dltProducerProps(KafkaProperties kafkaProperties,
                                                        String valueSerializer) {
        Map<String, Object> props = new java.util.HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers(kafkaProperties));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        return props;
    }

    private static List<String> bootstrapServers(KafkaProperties kafkaProperties) {
        List<String> consumerBootstrap = kafkaProperties.getConsumer().getBootstrapServers();
        return useIfPopulated(consumerBootstrap,
                useIfPopulated(kafkaProperties.getBootstrapServers(), List.of("localhost:9092")));
    }

    private static List<String> useIfPopulated(List<String> preferred, List<String> fallback) {
        return preferred != null && !preferred.isEmpty() ? preferred : fallback;
    }
}
