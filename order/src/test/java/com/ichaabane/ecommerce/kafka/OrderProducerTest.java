package com.ichaabane.ecommerce.kafka;

import com.ichaabane.ecommerce.customer.CustomerResponse;
import com.ichaabane.ecommerce.order.model.PaymentMethod;
import com.ichaabane.ecommerce.product.PurchaseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderProducer Unit Tests")
class OrderProducerTest {

    @Mock
    private KafkaTemplate<String, OrderConfirmation> kafkaTemplate;

    private OrderProducer producer;

    @BeforeEach
    void setUp() {
        producer = new OrderProducer(kafkaTemplate);
    }

    private OrderConfirmation confirmation() {
        return new OrderConfirmation(
                "REF-1",
                BigDecimal.valueOf(100),
                PaymentMethod.CREDIT_CARD,
                new CustomerResponse("c1", "John", "Doe", "john@doe.com"),
                List.of(new PurchaseResponse(21, "Book", "A book", BigDecimal.valueOf(50), 2)));
    }

    @Test
    @DisplayName("Should publish to order-topic with the confirmation as payload")
    void shouldPublishToOrderTopic() {
        var confirmation = confirmation();

        producer.sendOrderConfirmation(confirmation);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<OrderConfirmation>> captor = ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate, times(1)).send(captor.capture());
        assertEquals("order-topic", captor.getValue().getHeaders().get(KafkaHeaders.TOPIC));
        assertSame(confirmation, captor.getValue().getPayload());
    }

    @Test
    @DisplayName("Should forward every field of the confirmation unchanged")
    void shouldForwardConfirmationFields() {
        var confirmation = confirmation();

        producer.sendOrderConfirmation(confirmation);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<OrderConfirmation>> captor = ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate).send(captor.capture());
        assertEquals("REF-1", captor.getValue().getPayload().orderReference());
        assertEquals(1, captor.getValue().getPayload().products().size());
    }
}
