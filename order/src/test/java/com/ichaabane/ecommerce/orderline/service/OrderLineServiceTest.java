package com.ichaabane.ecommerce.orderline.service;

import com.ichaabane.ecommerce.orderline.dto.OrderLineRequest;
import com.ichaabane.ecommerce.orderline.dto.OrderLineResponse;
import com.ichaabane.ecommerce.orderline.mapper.OrderLineMapper;
import com.ichaabane.ecommerce.orderline.model.OrderLine;
import com.ichaabane.ecommerce.orderline.repository.OrderLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderLineService Unit Tests")
class OrderLineServiceTest {

    @Mock
    private OrderLineRepository repository;

    private final OrderLineMapper mapper = new OrderLineMapper();

    private OrderLineService service;

    @BeforeEach
    void setUp() {
        service = new OrderLineService(repository, mapper);
    }

    @Nested
    @DisplayName("saveOrderLine()")
    class SaveOrderLine {

        @Test
        @DisplayName("Should persist the mapped line and return the generated id")
        void shouldSaveAndReturnId() {
            // Return a persisted copy so the captured argument keeps its original (null) id.
            when(repository.save(org.mockito.ArgumentMatchers.any(OrderLine.class)))
                    .thenReturn(OrderLine.builder().id(11).build());

            Integer id = service.saveOrderLine(new OrderLineRequest(7, 21, 2));

            assertEquals(11, id);
            var captor = ArgumentCaptor.forClass(OrderLine.class);
            verify(repository).save(captor.capture());
            assertNull(captor.getValue().getId());
            assertEquals(7, captor.getValue().getOrder().getId());
            assertEquals(21, captor.getValue().getProductId());
            assertEquals(2, captor.getValue().getQuantity());
        }

        @Test
        @DisplayName("Should propagate a repository failure")
        void shouldPropagateFailure() {
            when(repository.save(org.mockito.ArgumentMatchers.any(OrderLine.class)))
                    .thenThrow(new RuntimeException("db down"));

            var exception = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> service.saveOrderLine(new OrderLineRequest(7, 21, 2)));

            assertEquals("db down", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("findAllByOrderId()")
    class FindAllByOrderId {

        @Test
        @DisplayName("Should map every line returned by the repository")
        void shouldMapEveryLine() {
            when(repository.findAllByOrderId(7)).thenReturn(List.of(
                    OrderLine.builder().id(1).productId(21).quantity(2).build(),
                    OrderLine.builder().id(2).productId(22).quantity(5).build()));

            List<OrderLineResponse> result = service.findAllByOrderId(7);

            assertEquals(2, result.size());
            assertEquals(1, result.get(0).id());
            assertEquals(5, result.get(1).quantity());
            verify(repository).findAllByOrderId(7);
        }

        @Test
        @DisplayName("Should return an empty list when the order has no lines")
        void shouldReturnEmptyList() {
            when(repository.findAllByOrderId(7)).thenReturn(List.of());

            assertTrue(service.findAllByOrderId(7).isEmpty());
        }
    }
}
