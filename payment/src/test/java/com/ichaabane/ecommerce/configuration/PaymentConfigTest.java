package com.ichaabane.ecommerce.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("KafkaPaymentTopicConfig Unit Tests")
class PaymentConfigTest {

    @Test
    @DisplayName("Should declare a topic named payment-topic")
    void shouldDeclarePaymentTopic() {
        NewTopic topic = new KafkaPaymentTopicConfig().paymentTopic();

        assertNotNull(topic);
        assertEquals("payment-topic", topic.name());
    }
}
