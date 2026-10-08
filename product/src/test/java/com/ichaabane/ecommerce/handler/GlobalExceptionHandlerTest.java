package com.ichaabane.ecommerce.handler;

import com.ichaabane.ecommerce.dto.request.ProductRequest;
import com.ichaabane.ecommerce.exception.ProductPurchaseException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GlobalExceptionHandler (product) Unit Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void dummyEndpoint(ProductRequest request) {
        // used only to build a MethodParameter
    }

    public static class Target {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        private String price;

        public String getPrice() {
            return price;
        }

        public void setPrice(String price) {
            this.price = price;
        }
    }

    private MethodArgumentNotValidException bindingException(String field, String message) throws Exception {
        var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", ProductRequest.class);
        var bindingResult = new BeanPropertyBindingResult(new Target(), "productRequest");
        bindingResult.rejectValue(field, "invalid", message);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    @Nested
    @DisplayName("ProductPurchaseException handling")
    class Purchase {

        @Test
        @DisplayName("Should map a purchase refusal to 400 with the exception message")
        void shouldMapPurchaseException() {
            var response = handler.handle(new ProductPurchaseException("Insufficient stock"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Insufficient stock", response.getBody());
        }
    }

    @Nested
    @DisplayName("EntityNotFoundException handling")
    class NotEntityFound {

        @Test
        @DisplayName("Should map a missing entity to 400 with the exception message")
        void shouldMapEntityNotFound() {
            var response = handler.handle(new EntityNotFoundException("Product not found with ID:: 9"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Product not found with ID:: 9", response.getBody());
        }
    }

    @Nested
    @DisplayName("MethodArgumentNotValidException handling")
    class Validation {

        @Test
        @DisplayName("Should map a field error to a 400 body keyed by field name")
        void shouldMapFieldError() throws Exception {
            var response = handler.handleMethodArgumentNotValidException(
                    bindingException("name", "Product name is required"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals("Product name is required", response.getBody().errors().get("name"));
        }

        @Test
        @DisplayName("Should collect several field errors")
        void shouldCollectSeveralErrors() throws Exception {
            var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", ProductRequest.class);
            var bindingResult = new BeanPropertyBindingResult(new Target(), "productRequest");
            bindingResult.rejectValue("name", "invalid", "name bad");
            bindingResult.rejectValue("price", "invalid", "price bad");
            var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

            var response = handler.handleMethodArgumentNotValidException(exception);

            assertEquals(Map.of("name", "name bad", "price", "price bad"), response.getBody().errors());
        }

        @Test
        @DisplayName("Should return an empty map when there are no field errors")
        void shouldReturnEmptyMap() throws Exception {
            var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", ProductRequest.class);
            var exception = new MethodArgumentNotValidException(
                    new MethodParameter(method, 0),
                    new BeanPropertyBindingResult(new Target(), "productRequest"));

            assertTrue(handler.handleMethodArgumentNotValidException(exception).getBody().errors().isEmpty());
        }
    }

    @Test
    @DisplayName("Should expose the error map through the record accessor")
    void shouldExposeErrorResponse() {
        assertEquals("bad", new ErrorResponse(Map.of("name", "bad")).errors().get("name"));
    }
}
