package com.ichaabane.ecommerce.order.controller;

import com.ichaabane.ecommerce.exception.BusinessException;
import com.ichaabane.ecommerce.handler.GlobalExceptionHandler;
import com.ichaabane.ecommerce.order.dto.OrderResponse;
import com.ichaabane.ecommerce.order.model.PaymentMethod;
import com.ichaabane.ecommerce.order.service.OrderService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController Unit Tests")
class OrderControllerTest {

    @Mock
    private OrderService service;

    @InjectMocks
    private OrderController controller;

    private MockMvc mockMvc;

    private static final String VALID_BODY =
            "{\"reference\":\"REF-1\",\"paymentMethod\":\"CREDIT_CARD\",\"customerId\":\"c1\","
                    + "\"products\":[{\"productId\":1,\"quantity\":2}]}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class CreateOrder {

        @Test
        @DisplayName("Should return 200 and the created order id")
        void shouldCreateOrder() throws Exception {
            when(service.createOrder(any())).thenReturn(7);

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(7));
        }

        @Test
        @DisplayName("Should return 400 when the reference is blank")
        void shouldRejectBlankReference() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reference\":\"  \",\"paymentMethod\":\"VISA\",\"customerId\":\"c1\",\"products\":[{\"productId\":1,\"quantity\":1}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.reference").exists());

            verify(service, never()).createOrder(any());
        }

        @Test
        @DisplayName("Should return 400 when the payment method is missing")
        void shouldRejectMissingPaymentMethod() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reference\":\"REF-1\",\"customerId\":\"c1\",\"products\":[{\"productId\":1,\"quantity\":1}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.paymentMethod").exists());
        }

        @Test
        @DisplayName("Should return 400 when the customer id is blank")
        void shouldRejectBlankCustomerId() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reference\":\"REF-1\",\"paymentMethod\":\"VISA\",\"customerId\":\" \",\"products\":[{\"productId\":1,\"quantity\":1}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.customerId").exists());
        }

        @Test
        @DisplayName("Should return 400 when the product list is empty")
        void shouldRejectEmptyProducts() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reference\":\"REF-1\",\"paymentMethod\":\"VISA\",\"customerId\":\"c1\",\"products\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.products").exists());
        }

        @Test
        @DisplayName("Should surface a business error as 400 with the message")
        void shouldSurfaceBusinessException() throws Exception {
            when(service.createOrder(any()))
                    .thenThrow(new BusinessException("Cannot create order:: No customer exists with the provided ID"));

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$").value("Cannot create order:: No customer exists with the provided ID"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders")
    class FindAll {

        @Test
        @DisplayName("Should return every order")
        void shouldReturnAllOrders() throws Exception {
            when(service.findAllOrders()).thenReturn(List.of(
                    new OrderResponse(1, "REF-1", BigDecimal.TEN, PaymentMethod.VISA, "c1"),
                    new OrderResponse(2, "REF-2", BigDecimal.ONE, PaymentMethod.PAYPAL, "c2")));

            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].reference").value("REF-2"));
        }

        @Test
        @DisplayName("Should return an empty array when there are no orders")
        void shouldReturnEmptyArray() throws Exception {
            when(service.findAllOrders()).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{id}")
    class FindById {

        @Test
        @DisplayName("Should return the order when it exists")
        void shouldReturnOrder() throws Exception {
            when(service.findById(3)).thenReturn(
                    new OrderResponse(3, "REF-3", BigDecimal.TEN, PaymentMethod.BITCOIN, "c9"));

            mockMvc.perform(get("/api/v1/orders/3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.paymentMethod").value("BITCOIN"));
        }

        @Test
        @DisplayName("Should return 404 when the order does not exist")
        void shouldReturn404() throws Exception {
            when(service.findById(99))
                    .thenThrow(new EntityNotFoundException("No order found with the provided ID: 99"));

            mockMvc.perform(get("/api/v1/orders/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$").value("No order found with the provided ID: 99"));
        }
    }
}
