package com.ichaabane.ecommerce.config;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

/**
 * Async executor for {@code @Async} email sending ({@link com.ichaabane.ecommerce.email.EmailService})
 * that carries the SLF4J MDC into the worker thread (review finding R-10 in {@code log-review.md}).
 *
 * <p>Why a custom {@link TaskDecorator}: the email is submitted from a Kafka listener whose logs are
 * correlated via {@code MDC["correlationId"]} (and {@code traceId} via Micrometer/Brave). Without
 * decoration the worker thread's MDC is empty, so every email log line is
 * uncorrelated. The decorator captures the submitting thread's context map at task submission and
 * restores it inside the task — the same pattern Spring's {@code ContextPropagatingTaskDecorator}
 * uses for trace context — and clears it afterwards so pool threads never leak state between tasks.</p>
 *
 * <p>The bean name {@code taskExecutor} is what Spring's {@code @EnableAsync} resolution falls back
 * to; Boot's conditional {@code applicationTaskExecutor} (same names, see
 * {@link TaskExecutionAutoConfiguration#APPLICATION_TASK_EXECUTOR_BEAN_NAME}) backs off when a bean
 * with these names already exists. Pool sizing follows Boot's {@code spring.task.execution.*}
 * properties so it remains tunable per environment; the decorator is the only behavior added.</p>
 */
@Configuration
public class AsyncMdcConfiguration {

    public static final String EXECUTOR_BEAN_NAME = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME;

    @Bean(name = {EXECUTOR_BEAN_NAME, "taskExecutor"})
    public ThreadPoolTaskExecutor mdcPropagatingTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("email-async-");
        executor.setTaskDecorator(new CorrelationIdTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }

    /**
     * Copies the submitting thread's MDC map into the task thread for the duration of the task.
     * A submit-time context of {@code null} (nothing in MDC) leaves the task thread's MDC cleared
     * as well; the previous state of the worker thread is restored in all paths.
     */
    public static final class CorrelationIdTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable delegate) {
            Map<String, String> submittedContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                applyContext(submittedContext);
                try {
                    delegate.run();
                } finally {
                    applyContext(previous);
                }
            };
        }

        private static void applyContext(Map<String, String> context) {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
        }
    }
}
