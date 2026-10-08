package com.ichaabane.ecommerce.handler;

import com.ichaabane.ecommerce.exception.BusinessException;
import com.ichaabane.ecommerce.order.dto.OrderRequest;
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

@DisplayName("GlobalExceptionHandler (order) Unit Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void dummyEndpoint(OrderRequest request) {
        // used only to build a MethodParameter
    }

    public static class Target {
        private String reference;

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        private String customerId;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
    }

    private MethodArgumentNotValidException bindingException() throws Exception {
        var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", OrderRequest.class);
        var bindingResult = new BeanPropertyBindingResult(new Target(), "orderRequest");
        bindingResult.rejectValue("reference", "invalid", "Order reference is required");
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    @Nested
    @DisplayName("EntityNotFoundException handling")
    class NotFound {

        @Test
        @DisplayName("Should map to 404 with the message")
        void shouldMapNotFound() {
            var response = handler.handle(new EntityNotFoundException("No order found with the provided ID: 9"));

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("No order found with the provided ID: 9", response.getBody());
        }
    }

    @Nested
    @DisplayName("BusinessException handling")
    class Business {

        @Test
        @DisplayName("Should map to 400 with the message")
        void shouldMapBusiness() {
            var response = handler.handle(new BusinessException("Cannot create order:: duplicated reference"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Cannot create order:: duplicated reference", response.getBody());
        }
    }

    @Nested
    @DisplayName("MethodArgumentNotValidException handling")
    class Validation {

        @Test
        @DisplayName("Should map a field error to a 400 body keyed by field name")
        void shouldMapFieldError() throws Exception {
            var response = handler.handleMethodArgumentNotValidException(bindingException());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Order reference is required", response.getBody().errors().get("reference"));
        }

        @Test
        @DisplayName("Should collect several field errors")
        void shouldCollectSeveralErrors() throws Exception {
            var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", OrderRequest.class);
            var bindingResult = new BeanPropertyBindingResult(new Target(), "orderRequest");
            bindingResult.rejectValue("reference", "invalid", "reference bad");
            bindingResult.rejectValue("customerId", "invalid", "customer bad");
            var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

            var response = handler.handleMethodArgumentNotValidException(exception);

            assertEquals(2, response.getBody().errors().size());
        }

        @Test
        @DisplayName("Should return an empty map when there are no field errors")
        void shouldReturnEmptyMap() throws Exception {
            var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", OrderRequest.class);
            var exception = new MethodArgumentNotValidException(
                    new MethodParameter(method, 0),
                    new BeanPropertyBindingResult(new Target(), "orderRequest"));

            assertTrue(handler.handleMethodArgumentNotValidException(exception).getBody().errors().isEmpty());
        }
    }
}
