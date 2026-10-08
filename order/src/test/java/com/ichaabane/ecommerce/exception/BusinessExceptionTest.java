package com.ichaabane.ecommerce.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BusinessException (order) Unit Tests")
class BusinessExceptionTest {

    @Test
    @DisplayName("Should expose the message through getMsg()")
    void shouldExposeMessage() {
        var exception = new BusinessException("Cannot create order:: duplicated reference");

        assertEquals("Cannot create order:: duplicated reference", exception.getMsg());
    }

    @Test
    @DisplayName("Should be a runtime exception")
    void shouldBeRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(BusinessException.class));
    }

    @Test
    @DisplayName("Should be equal to itself")
    void shouldBeEqualToItself() {
        var exception = new BusinessException("x");

        assertSame(exception, exception);
        assertEquals(exception, exception);
    }

    @Test
    @DisplayName("Should accept a null message")
    void shouldAcceptNullMessage() {
        assertNull(new BusinessException(null).getMsg());
    }
}
