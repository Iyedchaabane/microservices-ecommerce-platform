package com.ichaabane.ecommerce.order.mapper;

import com.ichaabane.ecommerce.order.dto.OrderRequest;
import com.ichaabane.ecommerce.order.dto.OrderResponse;
import com.ichaabane.ecommerce.order.model.Order;
import com.ichaabane.ecommerce.order.model.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("OrderMapper Unit Tests")
class OrderMapperTest {

    private final OrderMapper mapper = new OrderMapper();

    @Test
    @DisplayName("Should map the request and never the identifier")
    void shouldMapOrderRequest() {
        var request = new OrderRequest(
                "REF-1",
                PaymentMethod.PAYPAL,
                "customer-1",
                List.of()
        );

        Order order = mapper.toOrder(request, BigDecimal.valueOf(250));

        assertNull(order.getId());
        assertEquals("REF-1", order.getReference());
        assertEquals(BigDecimal.valueOf(250), order.getTotalAmount());
        assertEquals(PaymentMethod.PAYPAL, order.getPaymentMethod());
        assertEquals("customer-1", order.getCustomerId());
    }

    @Test
    @DisplayName("Should return null for a null request")
    void shouldReturnNullForNullRequest() {
        assertNull(mapper.toOrder(null, null));
    }

    @Test
    @DisplayName("Should map an order to its response")
    void shouldMapOrderResponse() {
        var order = Order.builder()
                .id(3)
                .reference("REF-3")
                .totalAmount(BigDecimal.TEN)
                .paymentMethod(PaymentMethod.VISA)
                .customerId("customer-3")
                .build();

        OrderResponse response = mapper.fromOrder(order);

        assertEquals(3, response.id());
        assertEquals("REF-3", response.reference());
        assertEquals(BigDecimal.TEN, response.amount());
        assertEquals(PaymentMethod.VISA, response.paymentMethod());
        assertEquals("customer-3", response.customerId());
    }
}