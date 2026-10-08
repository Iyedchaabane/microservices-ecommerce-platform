package com.ichaabane.ecommerce.payment.controller;

import com.ichaabane.ecommerce.handler.GlobalExceptionHandler;
import com.ichaabane.ecommerce.payment.service.PaymentService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController Unit Tests")
class PaymentControllerTest {

    @Mock
    private PaymentService service;

    @InjectMocks
    private PaymentController controller;

    private MockMvc mockMvc;

    private static final String VALID_BODY =
            "{\"amount\":100,\"paymentMethod\":\"CREDIT_CARD\",\"orderId\":1,\"orderReference\":\"REF-1\","
                    + "\"customer\":{\"id\":\"c1\",\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/payments happy path")
    class HappyPath {

        @Test
        @DisplayName("Should return 200 with the created payment id")
        void shouldCreatePayment() throws Exception {
            when(service.createPayment(any())).thenReturn(42);

            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(42));

            verify(service).createPayment(any());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments validation")
    class Validation {

        @Test
        @DisplayName("Should reject a negative amount")
        void shouldRejectNegativeAmount() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("\"amount\":100", "\"amount\":-5")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists());

            verify(service, never()).createPayment(any());
        }

        @Test
        @DisplayName("Should reject a zero amount")
        void shouldRejectZeroAmount() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY.replace("\"amount\":100", "\"amount\":0")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists());
        }

        @Test
        @DisplayName("Should reject a missing amount")
        void shouldRejectMissingAmount() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paymentMethod\":\"VISA\",\"orderId\":1,\"orderReference\":\"REF-1\","
                                    + "\"customer\":{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists());
        }

        @Test
        @DisplayName("Should reject a missing payment method")
        void shouldRejectMissingPaymentMethod() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100,\"orderId\":1,\"orderReference\":\"REF-1\","
                                    + "\"customer\":{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.paymentMethod").exists());
        }

        @Test
        @DisplayName("Should reject a missing order id")
        void shouldRejectMissingOrderId() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100,\"paymentMethod\":\"VISA\",\"orderReference\":\"REF-1\","
                                    + "\"customer\":{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.orderId").exists());
        }

        @Test
        @DisplayName("Should reject a blank order reference")
        void shouldRejectBlankOrderReference() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100,\"paymentMethod\":\"VISA\",\"orderId\":1,\"orderReference\":\"  \","
                                    + "\"customer\":{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"john@doe.com\"}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.orderReference").exists());
        }

        @Test
        @DisplayName("Should reject a missing customer")
        void shouldRejectMissingCustomer() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100,\"paymentMethod\":\"VISA\",\"orderId\":1,\"orderReference\":\"REF-1\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.customer").exists());
        }

        @Test
        @DisplayName("Should reject a customer with an invalid email")
        void shouldRejectInvalidCustomerEmail() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amount\":100,\"paymentMethod\":\"VISA\",\"orderId\":1,\"orderReference\":\"REF-1\","
                                    + "\"customer\":{\"firstname\":\"John\",\"lastname\":\"Doe\",\"email\":\"not-an-email\"}}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
