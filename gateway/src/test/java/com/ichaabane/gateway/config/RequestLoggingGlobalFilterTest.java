package com.ichaabane.gateway.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Focused unit tests for correlation-ID sanitization and the documented log
 * policy of {@link RequestLoggingGlobalFilter} (review findings S-2 and P-1).
 */
@DisplayName("RequestLoggingGlobalFilter — correlation sanitization and log policy")
class RequestLoggingGlobalFilterTest {

    private static final String HEADER = RequestLoggingGlobalFilter.CORRELATION_ID_HEADER;
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final RequestLoggingGlobalFilter filter = new RequestLoggingGlobalFilter();

    private static MockServerWebExchange exchangeWith(String headerValue) {
        var builder = MockServerHttpRequest.get("/api/v1/products");
        if (headerValue != null) {
            builder.header(HEADER, headerValue);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private GatewayFilterChain chainReturningNormally() {
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    /** Runs the filter and returns the exchange actually routed to the chain. */
    private ServerWebExchange routedExchange(MockServerWebExchange inbound, GatewayFilterChain chain) {
        var captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        filter.filter(inbound, chain).block();
        // The mock chain completes without committing the response; commit it so
        // the filter's beforeCommit hook (which echoes the correlation ID) fires.
        inbound.getResponse().setComplete().block();
        verify(chain).filter(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Correlation ID sanitization (S-2: log forging prevention)")
    class CorrelationSanitization {

        @Test
        @DisplayName("A safe client correlation ID is preserved and propagated downstream + echoed")
        void validIdPropagated() {
            var inbound = exchangeWith("order-abc-123");
            var chain = mock(GatewayFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            var routed = routedExchange(inbound, chain);

            assertEquals("order-abc-123", routed.getRequest().getHeaders().getFirst(HEADER));
            assertEquals("order-abc-123", inbound.getResponse().getHeaders().getFirst(HEADER));
        }

        @Test
        @DisplayName("CR/LF injection is replaced by a generated UUID before propagation")
        void crlfInjectionReplaced() {
            var inbound = exchangeWith("abc\r\nInjected-Header: 1");
            var chain = mock(GatewayFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            var routed = routedExchange(inbound, chain);

            String propagated = routed.getRequest().getHeaders().getFirst(HEADER);
            assertTrue(propagated != null && UUID_PATTERN.matcher(propagated).matches(),
                    "invalid input must be replaced by a generated UUID, got: " + propagated);
            assertFalse(propagated.contains("\r") || propagated.contains("\n"));
            assertEquals(propagated, inbound.getResponse().getHeaders().getFirst(HEADER));
        }

        @Test
        @DisplayName("Too-short IDs are replaced by a generated UUID")
        void shortIdReplaced() {
            var inbound = exchangeWith("1234567");
            var chain = mock(GatewayFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            var routed = routedExchange(inbound, chain);

            String propagated = routed.getRequest().getHeaders().getFirst(HEADER);
            assertTrue(propagated != null && UUID_PATTERN.matcher(propagated).matches());
        }

        @Test
        @DisplayName("Missing header generates a UUID and echoes it to the client")
        void absentGenerated() {
            var inbound = exchangeWith(null);
            var chain = mock(GatewayFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            var routed = routedExchange(inbound, chain);

            String propagated = routed.getRequest().getHeaders().getFirst(HEADER);
            assertTrue(propagated != null && UUID_PATTERN.matcher(propagated).matches());
            assertEquals(propagated, inbound.getResponse().getHeaders().getFirst(HEADER));
        }

        @Test
        @DisplayName("sanitize() accepts only [A-Za-z0-9._-]{8,64} — rejects CR/LF, spaces, extremes")
        void sanitizeContract() {
            assertEquals("order-abc-123", RequestLoggingGlobalFilter.sanitize("order-abc-123"));
            assertEquals("a.b_c-42", RequestLoggingGlobalFilter.sanitize("  a.b_c-42  "));
            String uuid = UUID.randomUUID().toString();
            assertEquals(uuid, RequestLoggingGlobalFilter.sanitize(uuid));

            assertNull(RequestLoggingGlobalFilter.sanitize("abc\r\nInjected-Header: 1"));
            assertNull(RequestLoggingGlobalFilter.sanitize("abc\nline2"));
            assertNull(RequestLoggingGlobalFilter.sanitize("id with spaces"));
            assertNull(RequestLoggingGlobalFilter.sanitize("short"));
            assertNull(RequestLoggingGlobalFilter.sanitize("a".repeat(65)));
            assertNull(RequestLoggingGlobalFilter.sanitize("id%00"));
            assertNull(RequestLoggingGlobalFilter.sanitize(""));
            assertNull(RequestLoggingGlobalFilter.sanitize(null));
        }
    }

    @Nested
    @DisplayName("Log noise policy (P-1)")
    class LogPolicy {

        @Test
        @DisplayName("Health probes bypass the filter entirely (no echo, no rewrite)")
        void healthBypassed() {
            var inbound = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/actuator/health").build());
            var chain = mock(GatewayFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            filter.filter(inbound, chain).block();

            var captor = ArgumentCaptor.forClass(ServerWebExchange.class);
            verify(chain).filter(captor.capture());
            assertNull(captor.getValue().getRequest().getHeaders().getFirst(HEADER),
                    "health requests must not be rewritten/decorated");
        }

        @Test
        @DisplayName("A normal completion produces no WARN/ERROR transport log")
        void normalCompletionQuiet() {
            var inbound = exchangeWith("order-abc-123");
            var chain = mock(GatewayFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            List<ILoggingEvent> events = captureLogs(() -> filter.filter(inbound, chain).block());

            assertEquals(0, events.stream()
                    .filter(event -> event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN))
                    .count(), "no WARN/ERROR expected for a normal completion: " + events);
        }

        @Test
        @DisplayName("A 5xx completion is escalated to WARN with status and duration")
        void serverErrorEscalatedToWarn() {
            var inbound = exchangeWith("order-abc-123");
            var chain = mock(GatewayFilterChain.class);
            when(chain.filter(any())).thenAnswer(inv -> {
                var routed = inv.getArgument(0, ServerWebExchange.class);
                routed.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                return Mono.empty();
            });

            List<ILoggingEvent> events = captureLogs(() -> filter.filter(inbound, chain).block());

            assertEquals(1, events.stream()
                    .filter(event -> event.getLevel().isGreaterOrEqual(ch.qos.logback.classic.Level.WARN))
                    .count(), "a 5xx completion must produce exactly one WARN event: " + events);
            assertTrue(events.get(0).getFormattedMessage().contains("server error"));
        }
    }

    private List<ILoggingEvent> captureLogs(Runnable action) {
        var logger = (Logger) LoggerFactory.getLogger(RequestLoggingGlobalFilter.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAndStopAllAppenders();
        }
        return List.copyOf(appender.list);
    }
}
