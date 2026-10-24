package com.ichaabane.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * Focused tests for inside-request MDC/echo behavior of the correlation-ID
 * filter, plus sanitization of client-supplied values (review finding S-2).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CorrelationIdFilter — validation, propagation and log-safety")
class CorrelationIdFilterTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern SAFE_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Mock
    private jakarta.servlet.FilterChain chain;

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private static MockHttpServletRequest requestWith(String headerValue) {
        var request = new MockHttpServletRequest("GET", "/api/v1/ping");
        if (headerValue != null) {
            request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, headerValue);
        }
        return request;
    }

    /** Runs the filter and captures the MDC state observed inside the downstream chain. */
    private String runAndCaptureInsideMdc(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        var insideMdc = new AtomicReference<String>();
        doAnswer(inv -> {
            insideMdc.set(MDC.get(CorrelationIdFilter.MDC_TRACE_ID_KEY));
            return null;
        }).when(chain).doFilter(any(), any());

        try {
            filter.doFilterInternal(request, response, chain);
        } finally {
            MDC.clear();
        }
        return insideMdc.get();
    }

    @Nested
    @DisplayName("Sanitization of client-supplied IDs")
    class Sanitization {

        @Test
        @DisplayName("sanitize() keeps safe values of the accepted shape")
        void keepsSafeValues() {
            assertTrue(SAFE_PATTERN.matcher("order-abc-123").matches());
            assertEquals("order-abc-123", CorrelationIdFilter.sanitize("order-abc-123"));
            String uuid = UUID.randomUUID().toString();
            assertEquals(uuid, CorrelationIdFilter.sanitize(uuid));
            assertEquals("a.b_c-42", CorrelationIdFilter.sanitize("  a.b_c-42  "));
        }

        @Test
        @DisplayName("sanitize() rejects CR/LF, control characters and unsafe shapes (log forging)")
        void rejectsUnsafeValues() {
            assertNull(CorrelationIdFilter.sanitize("abc\r\nInjected-Header: 1"));
            assertNull(CorrelationIdFilter.sanitize("abc\nline2"));
            assertNull(CorrelationIdFilter.sanitize("id with spaces"));
            assertNull(CorrelationIdFilter.sanitize("short"));
            assertNull(CorrelationIdFilter.sanitize("a".repeat(65)));
            assertNull(CorrelationIdFilter.sanitize(""));
            assertNull(CorrelationIdFilter.sanitize(null));
        }
    }

    @Nested
    @DisplayName("Request flow")
    class RequestFlow {

        @Test
        @DisplayName("Valid custom ID is preserved in MDC and echoed to the response")
        void validIdPreserved() throws Exception {
            var response = new MockHttpServletResponse();
            var inside = runAndCaptureInsideMdc(requestWith("order-abc-123"), response);

            assertEquals("order-abc-123", inside);
            assertEquals("order-abc-123", response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Valid UUID ID is preserved (gateway and tracing workflow)")
        void uuidPreserved() throws Exception {
            var id = UUID.randomUUID().toString();
            var response = new MockHttpServletResponse();
            var inside = runAndCaptureInsideMdc(requestWith(id), response);

            assertEquals(id, inside);
            assertEquals(id, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        }

        @Test
        @DisplayName("CR/LF injection is replaced by a generated UUID before use or echo")
        void crlfInjectionReplaced() throws Exception {
            var response = new MockHttpServletResponse();
            var inside = runAndCaptureInsideMdc(requestWith("abc\r\nInjected: 1"), response);

            assertTrue(inside != null && UUID_PATTERN.matcher(inside).matches(),
                    "MDC must carry a safe generated UUID, got: " + inside);
            String echoed = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
            assertTrue(echoed != null && UUID_PATTERN.matcher(echoed).matches(),
                    "response header must be a safe generated UUID, got: " + echoed);
            assertFalse(echoed.contains("\r") || echoed.contains("\n"));
        }

        @Test
        @DisplayName("Too-short IDs are replaced by a generated UUID")
        void shortIdReplaced() throws Exception {
            var response = new MockHttpServletResponse();
            var inside = runAndCaptureInsideMdc(requestWith("1234567"), response);

            assertTrue(UUID_PATTERN.matcher(inside).matches());
            assertNotEquals("1234567", response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        }

        @Test
        @DisplayName("Missing header generates a UUID and still echoes it")
        void absentGenerated() throws Exception {
            var response = new MockHttpServletResponse();
            var inside = runAndCaptureInsideMdc(requestWith(null), response);

            assertTrue(UUID_PATTERN.matcher(inside).matches());
            assertEquals(inside, response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER));
        }

        @Test
        @DisplayName("MDC contains no keys after the request completes")
        void mdcCleanedUp() throws Exception {
            filter.doFilterInternal(requestWith("order-abc-123"), new MockHttpServletResponse(), chain);

            // doFilterInternal's finally block already removed its keys
            assertNull(MDC.get(CorrelationIdFilter.MDC_TRACE_ID_KEY));
            assertNull(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY));
            MDC.clear();
        }
    }
}
