package com.ichaabane.ecommerce.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            RestTemplate restTemplate = new RestTemplateConfig(2000, 5000).restTemplate();

            assertNotNull(restTemplate);
            assertTrue(restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory,
                    "the timeout-carrying factory must be wired in");
        }

        @Test
        @DisplayName("Should configure explicit connect/read timeouts (R-3: no unbounded hangs)")
        void shouldConfigureTimeouts() {
            var config = new RestTemplateConfig(2000, 5000);

            assertEquals(2000, config.getConnectTimeoutMs());
            assertEquals(5000, config.getReadTimeoutMs());
        }

        @Test
        @DisplayName("Should fall back to documented defaults for invalid (non-positive) values")
        void shouldFallBackToDefaultsOnNegative() {
            var config = new RestTemplateConfig(-1, -1);

            assertEquals(2000, config.getConnectTimeoutMs());
            assertEquals(5000, config.getReadTimeoutMs());
        }
    }
}
