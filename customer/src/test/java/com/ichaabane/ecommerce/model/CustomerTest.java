package com.ichaabane.ecommerce.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Customer entity Unit Tests")
class CustomerTest {

    @Test
    @DisplayName("Should build a customer with the builder")
    void shouldBuildWithBuilder() {
        var address = new Address("Main St", "12", "75001");

        Customer customer = Customer.builder()
                .id("id-1").firstname("John").lastname("Doe").email("john@doe.com").address(address)
                .build();

        assertEquals("id-1", customer.getId());
        assertEquals("John", customer.getFirstname());
        assertEquals("Doe", customer.getLastname());
        assertEquals("john@doe.com", customer.getEmail());
        assertSame(address, customer.getAddress());
    }

    @Test
    @DisplayName("Should initialise every field to null for a default customer")
    void shouldInitialiseNulls() {
        Customer customer = new Customer();

        assertNull(customer.getId());
        assertNull(customer.getFirstname());
        assertNull(customer.getLastname());
        assertNull(customer.getEmail());
        assertNull(customer.getAddress());
    }

    @Test
    @DisplayName("Should use the all-args constructor")
    void shouldUseAllArgsConstructor() {
        var address = new Address("Other St", "9", "13001");

        Customer customer = new Customer("id-2", "Jane", "Smith", "jane@doe.com", address);

        assertEquals("id-2", customer.getId());
        assertEquals("Smith", customer.getLastname());
        assertSame(address, customer.getAddress());
    }

    @Test
    @DisplayName("Should allow mutation through the setters")
    void shouldAllowSetters() {
        Customer customer = new Customer();

        customer.setId("id-3");
        customer.setFirstname("Bob");
        customer.setLastname("Brown");
        customer.setEmail("bob@doe.com");

        assertEquals("id-3", customer.getId());
        assertEquals("Bob", customer.getFirstname());
        assertEquals("Brown", customer.getLastname());
        assertEquals("bob@doe.com", customer.getEmail());
    }
}
