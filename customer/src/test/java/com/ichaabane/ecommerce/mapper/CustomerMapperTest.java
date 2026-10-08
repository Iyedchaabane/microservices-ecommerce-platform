package com.ichaabane.ecommerce.mapper;

import com.ichaabane.ecommerce.dto.request.CustomerCreateRequest;
import com.ichaabane.ecommerce.dto.response.CustomerResponse;
import com.ichaabane.ecommerce.model.Address;
import com.ichaabane.ecommerce.model.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("CustomerMapper Unit Tests")
class CustomerMapperTest {

    private final CustomerMapper mapper = new CustomerMapper();

    @Test
    @DisplayName("Should map every create-request field")
    void shouldMapRequestFields() {
        var address = new Address("Main St", "12", "75001");
        var request = new CustomerCreateRequest("John", "Doe", "john@doe.com", address);

        Customer customer = mapper.toCustomer(request);

        assertEquals("John", customer.getFirstname());
        assertEquals("Doe", customer.getLastname());
        assertEquals("john@doe.com", customer.getEmail());
        assertSame(address, customer.getAddress());
    }

    @Test
    @DisplayName("Should never assign an identifier when mapping a create request")
    void shouldNeverAssignAnIdentifier() {
        Customer customer = mapper.toCustomer(new CustomerCreateRequest("John", "Doe", "john@doe.com", null));

        assertNull(customer.getId());
    }

    @Test
    @DisplayName("Should keep a null address when the request has none")
    void shouldKeepNullAddress() {
        Customer customer = mapper.toCustomer(new CustomerCreateRequest("John", "Doe", "john@doe.com", null));

        assertNull(customer.getAddress());
    }

    @Test
    @DisplayName("Should return null for a null request")
    void shouldReturnNullForNullRequest() {
        assertNull(mapper.toCustomer(null));
    }

    @Test
    @DisplayName("Should map an entity to its response")
    void shouldMapEntityToResponse() {
        var address = new Address("Main St", "12", "75001");
        var customer = Customer.builder()
                .id("id-9").firstname("Jane").lastname("Smith").email("jane@doe.com").address(address)
                .build();

        CustomerResponse response = mapper.fromCustomer(customer);

        assertEquals("id-9", response.id());
        assertEquals("Jane", response.firstname());
        assertEquals("Smith", response.lastname());
        assertEquals("jane@doe.com", response.email());
        assertSame(address, response.address());
    }

    @Test
    @DisplayName("Should keep a null address when mapping the response")
    void shouldKeepNullAddressOnResponse() {
        var customer = Customer.builder().id("id-1").firstname("Jane").build();

        CustomerResponse response = mapper.fromCustomer(customer);

        assertNull(response.address());
        assertNull(response.email());
    }

    @Test
    @DisplayName("Should return null for a null entity")
    void shouldReturnNullForNullEntity() {
        assertNull(mapper.fromCustomer(null));
    }
}
