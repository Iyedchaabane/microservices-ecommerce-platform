package com.ichaabane.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AsyncMdcConfiguration")
class AsyncMdcConfigurationTest {

    @Test
    @DisplayName("Decorator makes the submitting thread's MDC visible inside the task")
    void decoratorCarriesMdcIntoTask() throws Exception {
        var decorator = new AsyncMdcConfiguration.CorrelationIdTaskDecorator();

        MDC.put("correlationId", "correlation-deco-1");
        try {
            var seen = new java.util.concurrent.atomic.AtomicReference<String>();
            var done = new CountDownLatch(1);
            Runnable decorated = decorator.decorate(() -> {
                seen.set(MDC.get("correlationId"));
                done.countDown();
            });
            new Thread(decorated).start();
            assertTrue(done.await(5, TimeUnit.SECONDS), "task did not run");
            assertEquals("correlation-deco-1", seen.get(), "MDC correlationId must reach the task thread");
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("Decorator restores the worker thread's own pre-existing MDC after the task")
    void decoratorRestoresWorkerContext() {
        var decorator = new AsyncMdcConfiguration.CorrelationIdTaskDecorator();

        // Submit-time snapshot (caller thread state).
        MDC.put("correlationId", "correlation-deco-2");
        try {
            Runnable decorated = decorator.decorate(() -> {
                assertEquals("correlation-deco-2", MDC.get("correlationId"));
            });
            // Simulate a pool worker that already carries its own context.
            MDC.clear();
            MDC.put("worker-local", "w1");

            decorated.run();

            assertEquals("w1", MDC.get("worker-local"),
                    "worker thread context must be restored after the task");
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("Executor bean is configured with the MDC decorator")
    void executorBeanUsesDecorator() throws Exception {
        var executor = new AsyncMdcConfiguration().mdcPropagatingTaskExecutor();
        var config = new AsyncMdcConfiguration.CorrelationIdTaskDecorator();

        // Initialize to allow the decorator to be applied to submitted tasks.
        executor.initialize();
        try {
            var seen = new java.util.concurrent.atomic.AtomicReference<String>();
            var done = new CountDownLatch(1);
            MDC.put("correlationId", "correlation-pool-1");
            try {
                executor.execute(() -> {
                    seen.set(MDC.get("correlationId"));
                    done.countDown();
                });
            } finally {
                MDC.clear();
            }
            assertTrue(done.await(5, TimeUnit.SECONDS), "task did not run on the executor");
            assertEquals("correlation-pool-1", seen.get(), "executor must run tasks with the submitting thread's MDC");
            assertNull(MDC.get("correlationId"), "submitting thread keeps no copy afterwards");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Executor bean name matches what @EnableAsync resolves by default")
    void executorBeanNameIsApplicationTaskExecutor() {
        assertEquals("applicationTaskExecutor", AsyncMdcConfiguration.EXECUTOR_BEAN_NAME);
        assertNotNull(AsyncMdcConfiguration.class);
    }
}
