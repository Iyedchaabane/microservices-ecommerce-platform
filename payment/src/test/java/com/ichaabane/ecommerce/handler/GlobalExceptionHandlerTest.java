package com.ichaabane.ecommerce.handler;

import com.ichaabane.ecommerce.exception.BusinessException;
import com.ichaabane.ecommerce.payment.dto.PaymentRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GlobalExceptionHandler (payment) Unit Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void dummyEndpoint(PaymentRequest request) {
        // used only to build a MethodParameter
    }

    public static class Target {
        private String orderReference;

        public String getOrderReference() {
            return orderReference;
        }

        public void setOrderReference(String orderReference) {
            this.orderReference = orderReference;
        }
    }

    private MethodArgumentNotValidException bindingException() throws Exception {
        var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", PaymentRequest.class);
        var bindingResult = new BeanPropertyBindingResult(new Target(), "paymentRequest");
        bindingResult.rejectValue("orderReference", "invalid", "Order reference is required");
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    @Nested
    @DisplayName("EntityNotFoundException handling")
    class NotFound {

        @Test
        @DisplayName("Should map to 404 with the message")
        void shouldMapNotFound() {
            var response = handler.handle(new EntityNotFoundException("Payment not found"));

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("Payment not found", response.getBody());
        }
    }

    @Nested
    @DisplayName("BusinessException handling")
    class Business {

        @Test
        @DisplayName("Should map to 400 with the message")
        void shouldMapBusiness() {
            var response = handler.handle(new BusinessException("Payment declined"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Payment declined", response.getBody());
        }
    }

    @Nested
    @DisplayName("MethodArgumentNotValidException handling")
    class Validation {

        @Test
        @DisplayName("Should key the error body by field name")
        void shouldMapFieldError() throws Exception {
            var response = handler.handleMethodArgumentNotValidException(bindingException());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Order reference is required", response.getBody().errors().get("orderReference"));
        }

        @Test
        @DisplayName("Should return an empty map when there are no field errors")
        void shouldReturnEmptyMap() throws Exception {
            var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", PaymentRequest.class);
            var exception = new MethodArgumentNotValidException(
                    new MethodParameter(method, 0),
                    new BeanPropertyBindingResult(new Target(), "paymentRequest"));

            assertTrue(handler.handleMethodArgumentNotValidException(exception).getBody().errors().isEmpty());
        }

        @Test
        @DisplayName("Should expose the error map through the record accessor")
        void shouldExposeErrorResponse() {
            assertEquals("bad", new ErrorResponse(java.util.Map.of("amount", "bad")).errors().get("amount"));
        }
    }
}
