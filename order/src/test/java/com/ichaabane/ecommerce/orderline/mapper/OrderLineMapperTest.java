package com.ichaabane.ecommerce.orderline.mapper;

import com.ichaabane.ecommerce.orderline.dto.OrderLineRequest;
import com.ichaabane.ecommerce.orderline.dto.OrderLineResponse;
import com.ichaabane.ecommerce.orderline.model.OrderLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("OrderLineMapper Unit Tests")
class OrderLineMapperTest {

    private final OrderLineMapper mapper = new OrderLineMapper();

    @Test
    @DisplayName("Should reference the order without assigning a primary key")
    void shouldNotAssignIdentifier() {
        var request = new OrderLineRequest(7, 21, 2);

        OrderLine orderLine = mapper.toOrderLine(request);

        // Regression guard: the primary key used to be set to the order id, which
        // made every line of an order overwrite the previous one.
        assertNull(orderLine.getId());
        assertEquals(7, orderLine.getOrder().getId());
        assertEquals(21, orderLine.getProductId());
        assertEquals(2, orderLine.getQuantity());
    }

    @Test
    @DisplayName("Should map an order line to its response")
    void shouldMapOrderLineResponse() {
        var orderLine = OrderLine.builder().id(5).productId(21).quantity(3).build();

        OrderLineResponse response = mapper.toOrderLineResponse(orderLine);

        assertEquals(5, response.id());
        assertEquals(3, response.quantity());
    }
}