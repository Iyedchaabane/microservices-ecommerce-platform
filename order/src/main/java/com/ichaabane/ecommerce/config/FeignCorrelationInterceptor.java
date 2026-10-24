package com.ichaabane.ecommerce.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

/**
 * Feign client request interceptor that propagates correlation/trace ID
 * across downstream HTTP calls to other services.
 */
@Configuration
public class FeignCorrelationInterceptor implements RequestInterceptor {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public void apply(RequestTemplate template) {
        // Correlation fix (audit finding F-21): propagate the gateway-issued
        // X-Correlation-Id (MDC "correlationId" from CorrelationIdFilter); the
        // trace id from the tracing bridge is only a fallback, never the
        // primary contract.
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("traceId");
        }
        if (correlationId != null && !correlationId.isBlank()) {
            template.header(CORRELATION_ID_HEADER, correlationId);
        }
    }
}
