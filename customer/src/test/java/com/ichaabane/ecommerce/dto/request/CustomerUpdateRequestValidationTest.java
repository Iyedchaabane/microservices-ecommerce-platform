package com.ichaabane.ecommerce.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CustomerUpdateRequest Bean Validation Tests")
class CustomerUpdateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> violatedFields(CustomerUpdateRequest request) {
        return validator.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Should accept an empty request: every field is optional in a PATCH-style update")
    void shouldAcceptEmptyRequest() {
        assertTrue(violatedFields(new CustomerUpdateRequest(null, null, null, null)).isEmpty());
    }

    @Test
    @DisplayName("Should accept blank firstname and lastname (treated as 'not provided')")
    void shouldAcceptBlankFields() {
        assertTrue(violatedFields(new CustomerUpdateRequest("  ", "  ", null, null)).isEmpty());
    }

    @Test
    @DisplayName("Should accept a request with only an address")
    void shouldAcceptAddressOnly() {
        assertTrue(violatedFields(new CustomerUpdateRequest(null, null, null,
                new com.ichaabane.ecommerce.model.Address("Main St", "12", "75001"))).isEmpty());
    }

    @Test
    @DisplayName("Should accept a valid email")
    void shouldAcceptValidEmail() {
        assertTrue(violatedFields(new CustomerUpdateRequest(null, null, "john@doe.com", null)).isEmpty());
    }

    @Test
    @DisplayName("Should reject a malformed email when provided")
    void shouldRejectMalformedEmail() {
        assertTrue(violatedFields(new CustomerUpdateRequest(null, null, "not-an-email", null)).contains("email"));
    }

    @Test
    @DisplayName("Should reject an email without a domain when provided")
    void shouldRejectEmailWithoutDomain() {
        assertTrue(violatedFields(new CustomerUpdateRequest(null, null, "john@", null)).contains("email"));
    }
}
