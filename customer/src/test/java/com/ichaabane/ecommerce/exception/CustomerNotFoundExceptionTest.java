package com.ichaabane.ecommerce.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CustomerNotFoundException Unit Tests")
class CustomerNotFoundExceptionTest {

    @Test
    @DisplayName("Should expose the message through getMsg()")
    void shouldExposeMessage() {
        var exception = new CustomerNotFoundException("No customer found with the provided ID: 999");

        assertEquals("No customer found with the provided ID: 999", exception.getMsg());
    }

    @Test
    @DisplayName("Should be a runtime exception")
    void shouldBeRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(CustomerNotFoundException.class));
    }

    @Test
    @DisplayName("Should be equal to itself (identity semantics from callSuper)")
    void shouldBeEqualToItself() {
        var exception = new CustomerNotFoundException("same");

        assertSame(exception, exception);
        assertEquals(exception, exception);
    }

    @Test
    @DisplayName("Should accept a null message")
    void shouldAcceptNullMessage() {
        assertNull(new CustomerNotFoundException(null).getMsg());
    }
}
