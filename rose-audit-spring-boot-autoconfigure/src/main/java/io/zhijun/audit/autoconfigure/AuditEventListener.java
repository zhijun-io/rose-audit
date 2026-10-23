package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditEventConsumer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;

/**
 * Coordinates transaction timing and asynchronous delivery for the configured {@link AuditEventConsumer}. A successful
 * event is enqueued immediately because it records an observed execution fact, independent of the enclosing business
 * transaction. Queue rejection and consumer failures are logged and are not retried; the in-memory queue is not durable.
 */
final class AuditEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditEventConsumer consumer;
    private final TaskExecutor taskExecutor;
    private final Metrics metrics;
    private final FailureLogLimiter failureLogLimiter = new FailureLogLimiter(System::nanoTime);

    AuditEventListener(AuditEventConsumer consumer, TaskExecutor taskExecutor) {
        this(consumer, taskExecutor, Metrics.NOOP);
    }

    AuditEventListener(AuditEventConsumer consumer, TaskExecutor taskExecutor, Metrics metrics) {
        this.consumer = consumer;
        this.taskExecutor = taskExecutor;
        this.metrics = metrics;
    }

    @EventListener
    void consume(AuditEvent event) {
        enqueue(event);
    }

    private void enqueue(AuditEvent event) {
        try {
            taskExecutor.execute(() -> consumeSafely(event));
            metrics.queued(event.outcome());
        } catch (RuntimeException dispatchFailure) {
            metrics.failed("enqueue", event.outcome());
            logFailure("queued", event, dispatchFailure);
        }
    }

    private void consumeSafely(AuditEvent event) {
        long startedAt = System.nanoTime();
        try {
            consumer.consume(event);
            metrics.consumed(event.outcome());
        } catch (RuntimeException consumeFailure) {
            metrics.failed("consume", event.outcome());
            logFailure("consumed", event, consumeFailure);
        } finally {
            metrics.consumerDuration(event.outcome(), System.nanoTime() - startedAt);
        }
    }

    private void logFailure(String stage, AuditEvent event, RuntimeException failure) {
        if (!failureLogLimiter.shouldLog()) return;
        LOGGER.error(
                "Audit event could not be {} (type={}, outcome={}, cause={}, suppressed={})",
                stage,
                event.type(),
                event.outcome(),
                failure.getClass().getName(),
                failureLogLimiter.takeSuppressed());
    }

    /** Allows one failure log per interval while metrics continue to count every failure. */
    static final class FailureLogLimiter {
        private static final long INTERVAL_NANOS = Duration.ofSeconds(10).toNanos();
        private final LongSupplier nanoTime;
        private final AtomicLong nextLogAt = new AtomicLong();
        private final AtomicLong suppressed = new AtomicLong();

        FailureLogLimiter(LongSupplier nanoTime) {
            this.nanoTime = nanoTime;
        }

        boolean shouldLog() {
            long now = nanoTime.getAsLong();
            long next = nextLogAt.get();
            if (now < next || !nextLogAt.compareAndSet(next, now + INTERVAL_NANOS)) {
                suppressed.incrementAndGet();
                return false;
            }
            return true;
        }

        long takeSuppressed() {
            return suppressed.getAndSet(0);
        }
    }

    /** Records queued, consumed, failed-stage, and duration measurements without coupling the listener to Micrometer. */
    interface Metrics {
        Metrics NOOP = new Metrics() {};

        default void queued(AuditEvent.Outcome outcome) {}

        default void consumed(AuditEvent.Outcome outcome) {}

        default void failed(String stage, AuditEvent.Outcome outcome) {}

        default void consumerDuration(AuditEvent.Outcome outcome, long durationNanos) {}
    }
}
