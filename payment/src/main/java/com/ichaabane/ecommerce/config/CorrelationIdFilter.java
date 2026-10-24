package com.ichaabane.ecommerce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;/**
 * Servlet filter for distributed tracing and correlation ID propagation in payment service.
 *
 * <p>Ensures every incoming HTTP request has an {@code X-Correlation-Id} header,
 * registers it in SLF4J MDC for structured logging, and echoes it in the response header.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";
    public static final String MDC_CORRELATION_ID_KEY = "correlationId";

    /**
     * A correlation ID supplied by the client is trusted only when it is safe
     * to write verbatim into logs and response headers: 8–64 characters of
     * {@code [A-Za-z0-9._-]}. This rejects CR/LF, control characters and
     * excessive length, preventing log forging via request headers (review
     * finding S-2 in {@code log-review.md}). Invalid or missing values fall
     * back to the routing trace in the MDC, then to a generated UUID.
     */
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    /** Returns the candidate only when it is safe to use/log; otherwise {@code null}. */
    static String sanitize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        return SAFE_CORRELATION_ID.matcher(trimmed).matches() ? trimmed : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = sanitize(request.getHeader(CORRELATION_ID_HEADER));
        if (correlationId == null) {
            String existingTrace = MDC.get(MDC_TRACE_ID_KEY);
            if (existingTrace != null && !existingTrace.isBlank()) {
                correlationId = existingTrace;
            } else {
                correlationId = UUID.randomUUID().toString();
            }
        }

        boolean setTraceId = false;
        if (MDC.get(MDC_TRACE_ID_KEY) == null || MDC.get(MDC_TRACE_ID_KEY).isBlank()) {
            MDC.put(MDC_TRACE_ID_KEY, correlationId);
            setTraceId = true;
        }
        MDC.put(MDC_CORRELATION_ID_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID_KEY);
            if (setTraceId) {
                MDC.remove(MDC_TRACE_ID_KEY);
            }
        }
    }
}
