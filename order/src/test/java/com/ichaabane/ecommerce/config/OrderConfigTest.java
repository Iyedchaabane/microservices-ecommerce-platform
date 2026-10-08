package com.ichaabane.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Order configuration beans Unit Tests")
class OrderConfigTest {

    @Nested
    @DisplayName("KafkaOrderTopicConfig")
    class KafkaTopic {

        @Test
        @DisplayName("Should declare a topic named order-topic")
        void shouldDeclareOrderTopic() {
            NewTopic topic = new KafkaOrderTopicConfig().orderTopic();

            assertNotNull(topic);
            assertEquals("order-topic", topic.name());
        }
    }

    @Nested
    @DisplayName("RestTemplateConfig")
    class RestTemplateBean {

        @Test
        @DisplayName("Should expose a RestTemplate bean")
        void shouldExposeRestTemplate() {
            RestTemplate restTemplate = new RestTemplateConfig().restTemplate();

            assertNotNull(restTemplate);
        }
    }
}
