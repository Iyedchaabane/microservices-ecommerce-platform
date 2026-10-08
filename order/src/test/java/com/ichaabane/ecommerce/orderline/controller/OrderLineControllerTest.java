package com.ichaabane.ecommerce.orderline.controller;

import com.ichaabane.ecommerce.orderline.dto.OrderLineResponse;
import com.ichaabane.ecommerce.orderline.service.OrderLineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderLineController Unit Tests")
class OrderLineControllerTest {

    @Mock
    private OrderLineService service;

    @InjectMocks
    private OrderLineController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Should return the order lines of the requested order")
    void shouldReturnOrderLines() throws Exception {
        when(service.findAllByOrderId(7)).thenReturn(List.of(
                new OrderLineResponse(1, 2),
                new OrderLineResponse(2, 3)));

        mockMvc.perform(get("/api/v1/order-lines/order/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].quantity").value(3));

        verify(service, times(1)).findAllByOrderId(7);
    }

    @Test
    @DisplayName("Should return an empty array when the order has no lines")
    void shouldReturnEmptyArray() throws Exception {
        when(service.findAllByOrderId(8)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/order-lines/order/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
