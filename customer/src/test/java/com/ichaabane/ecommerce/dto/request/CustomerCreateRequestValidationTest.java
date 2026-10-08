package com.ichaabane.ecommerce.dto.request;

import com.ichaabane.ecommerce.model.Address;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CustomerCreateRequest Bean Validation Tests")
class CustomerCreateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> violatedFields(CustomerCreateRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Should accept a fully valid request")
    void shouldAcceptValidRequest() {
        var request = new CustomerCreateRequest("John", "Doe", "john@doe.com",
                new Address("Main St", "12", "75001"));

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("Should accept a request without an address")
    void shouldAcceptMissingAddress() {
        assertTrue(violatedFields(new CustomerCreateRequest("John", "Doe", "john@doe.com", null)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a null firstname")
    void shouldRejectNullFirstname() {
        assertTrue(violatedFields(new CustomerCreateRequest(null, "Doe", "john@doe.com", null)).contains("firstname"));
    }

    @Test
    @DisplayName("Should reject a blank firstname")
    void shouldRejectBlankFirstname() {
        assertTrue(violatedFields(new CustomerCreateRequest("   ", "Doe", "john@doe.com", null)).contains("firstname"));
    }

    @Test
    @DisplayName("Should reject a null lastname")
    void shouldRejectNullLastname() {
        assertTrue(violatedFields(new CustomerCreateRequest("John", null, "john@doe.com", null)).contains("lastname"));
    }

    @Test
    @DisplayName("Should reject a blank lastname")
    void shouldRejectBlankLastname() {
        assertTrue(violatedFields(new CustomerCreateRequest("John", "  ", "john@doe.com", null)).contains("lastname"));
    }

    @Test
    @DisplayName("Should reject a null email")
    void shouldRejectNullEmail() {
        assertTrue(violatedFields(new CustomerCreateRequest("John", "Doe", null, null)).contains("email"));
    }

    @Test
    @DisplayName("Should reject a blank email")
    void shouldRejectBlankEmail() {
        assertTrue(violatedFields(new CustomerCreateRequest("John", "Doe", "  ", null)).contains("email"));
    }

    @Test
    @DisplayName("Should reject a malformed email")
    void shouldRejectMalformedEmail() {
        assertTrue(violatedFields(new CustomerCreateRequest("John", "Doe", "not-an-email", null)).contains("email"));
    }

    @Test
    @DisplayName("Should reject an email without a domain")
    void shouldRejectEmailWithoutDomain() {
        assertTrue(violatedFields(new CustomerCreateRequest("John", "Doe", "john@", null)).contains("email"));
    }

    @Test
    @DisplayName("Should report one violation per invalid field")
    void shouldReportAllInvalidFields() {
        CustomerCreateRequest request = new CustomerCreateRequest(null, null, null, null);

        assertEquals(3, validator.validate(request).size());
    }

    @Test
    @DisplayName("Should expose the configured message for a blank field")
    void shouldExposeConfiguredMessage() {
        Set<jakarta.validation.ConstraintViolation<CustomerCreateRequest>> violations =
                validator.validate(new CustomerCreateRequest(null, "Doe", "john@doe.com", null));

        String message = violations.iterator().next().getMessage();
        assertEquals("Customer firstname is required", message);
    }
}
