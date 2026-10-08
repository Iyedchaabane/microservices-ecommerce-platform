package com.ichaabane.ecommerce.handler;

import com.ichaabane.ecommerce.dto.request.CustomerCreateRequest;
import com.ichaabane.ecommerce.exception.CustomerEmailAlreadyExistsException;
import com.ichaabane.ecommerce.exception.CustomerNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GlobalExceptionHandler (customer) Unit Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unused")
    private void dummyEndpoint(CustomerCreateRequest request) {
        // used only to build a MethodParameter for MethodArgumentNotValidException
    }

    /** Simple bean exposing the fields the handler must report. */
    public static class Target {
        private String firstname;
        private String lastname;
        private String email;

        public String getFirstname() {
            return firstname;
        }

        public void setFirstname(String firstname) {
            this.firstname = firstname;
        }

        public String getLastname() {
            return lastname;
        }

        public void setLastname(String lastname) {
            this.lastname = lastname;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    private MethodArgumentNotValidException bindingException(String field, String message) throws Exception {
        var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", CustomerCreateRequest.class);
        var parameter = new MethodParameter(method, 0);
        var bindingResult = new BeanPropertyBindingResult(new Target(), "customerRequest");
        bindingResult.rejectValue(field, "invalid", message);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @Nested
    @DisplayName("CustomerNotFoundException handling")
    class NotFound {

        @Test
        @DisplayName("Should map the exception to a 404 with the message as body")
        void shouldMapToNotFound() {
            var response = handler.handle(new CustomerNotFoundException("No customer with id 42"));

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals("No customer with id 42", response.getBody());
        }
    }

    @Nested
    @DisplayName("CustomerEmailAlreadyExistsException handling")
    class EmailAlreadyExists {

        @Test
        @DisplayName("Should map the exception to a 409 with the message as body")
        void shouldMapToConflict() {
            var response = handler.handle(new CustomerEmailAlreadyExistsException(
                    "Customer with email john@doe.com already exists"));

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertEquals("Customer with email john@doe.com already exists", response.getBody());
        }
    }

    @Nested
    @DisplayName("DuplicateKeyException handling")
    class DuplicateKey {

        @Test
        @DisplayName("Should map an untranslated duplicate-key error to a generic 409")
        void shouldMapToGenericConflict() {
            var response = handler.handleDuplicateKey(new DuplicateKeyException(
                    "E11000 duplicate key error collection: customer.customer index: email dup key"));

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertEquals("The request conflicts with an existing resource", response.getBody());
        }

        @Test
        @DisplayName("Should not leak MongoDB internals in the response body")
        void shouldNotLeakMongoInternals() {
            var response = handler.handleDuplicateKey(new DuplicateKeyException(
                "E11000 duplicate key error collection: customer.customer index: email dup key"));

            assertTrue(!response.getBody().contains("E11000"));
            assertTrue(!response.getBody().contains("customer.customer"));
        }
    }

    @Nested
    @DisplayName("MethodArgumentNotValidException handling")
    class Validation {

        @Test
        @DisplayName("Should map a field error to a 400 with a field -> message body")
        void shouldMapFieldError() throws Exception {
            var response = handler.handleMethodArgumentNotValidException(
                    bindingException("firstname", "Customer firstname is required"));

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            var body = response.getBody();
            assertEquals("Customer firstname is required", body.errors().get("firstname"));
        }

        @Test
        @DisplayName("Should collect multiple field errors")
        void shouldCollectMultipleErrors() throws Exception {
            var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", CustomerCreateRequest.class);
            var bindingResult = new BeanPropertyBindingResult(new Target(), "customerRequest");
            bindingResult.rejectValue("firstname", "invalid", "firstname bad");
            bindingResult.rejectValue("email", "invalid", "email bad");
            var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

            var response = handler.handleMethodArgumentNotValidException(exception);

            assertEquals(2, response.getBody().errors().size());
            assertEquals("firstname bad", response.getBody().errors().get("firstname"));
            assertEquals("email bad", response.getBody().errors().get("email"));
        }

        @Test
        @DisplayName("Should return an empty error map when there is no field error")
        void shouldReturnEmptyMapWithoutErrors() throws Exception {
            var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", CustomerCreateRequest.class);
            var exception = new MethodArgumentNotValidException(
                    new MethodParameter(method, 0),
                    new BeanPropertyBindingResult(new Target(), "customerRequest"));

            var response = handler.handleMethodArgumentNotValidException(exception);

            assertTrue(response.getBody().errors().isEmpty());
        }
    }

    @Nested
    @DisplayName("ErrorResponse body")
    class ErrorResponseMapping {

        @Test
        @DisplayName("Should expose the error map through the record accessor")
        void shouldExposeErrors() {
            var errorResponse = new ErrorResponse(java.util.Map.of("email", "bad"));

            assertEquals("bad", errorResponse.errors().get("email"));
        }

        @Test
        @DisplayName("Should allow a null error map (record semantics)")
        void shouldAllowNullMap() {
            assertNull(new ErrorResponse(null).errors());
        }
    }
}
