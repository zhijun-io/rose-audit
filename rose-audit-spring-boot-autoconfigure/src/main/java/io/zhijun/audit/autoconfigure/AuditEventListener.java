package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Coordinates transaction timing and asynchronous delivery for the configured {@link AuditEventConsumer}. A successful
 * event is enqueued only after commit; it is discarded when the business transaction rolls back. A failure event is
 * enqueued after transaction completion, whether the transaction committed or rolled back. Events published without an
 * active transaction are enqueued immediately. If a transaction is active without synchronization support, the event
 * is logged and discarded rather than consumed before transaction completion. Queue rejection and consumer failures
 * are logged and are not retried; the in-memory queue is not durable.
 */
final class AuditEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditEventConsumer consumer;
    private final TaskExecutor taskExecutor;
    private final Metrics metrics;

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
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            enqueue(event);
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            metrics.failed("defer", event.outcome());
            LOGGER.error(
                    "Audit event could not be deferred because transaction synchronization is inactive "
                            + "(action={}, outcome={})",
                    event.action(),
                    event.outcome());
            return;
        }

        TransactionSynchronization synchronization = new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (event.outcome() == AuditEvent.Outcome.SUCCESS) {
                    enqueue(event);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (event.outcome() == AuditEvent.Outcome.FAILURE) {
                    enqueue(event);
                }
            }
        };

        try {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        } catch (RuntimeException registrationFailure) {
            metrics.failed("defer", event.outcome());
            LOGGER.error(
                    "Audit event could not be deferred (action={}, outcome={})",
                    event.action(),
                    event.outcome(),
                    registrationFailure);
        }
    }

    private void enqueue(AuditEvent event) {
        try {
            taskExecutor.execute(() -> consumeSafely(event));
            metrics.queued(event.outcome());
        } catch (RuntimeException dispatchFailure) {
            metrics.failed("enqueue", event.outcome());
            LOGGER.error(
                    "Audit event could not be queued (action={}, outcome={})",
                    event.action(),
                    event.outcome(),
                    dispatchFailure);
        }
    }

    private void consumeSafely(AuditEvent event) {
        long startedAt = System.nanoTime();
        try {
            consumer.consume(event);
            metrics.consumed(event.outcome());
        } catch (RuntimeException consumeFailure) {
            metrics.failed("consume", event.outcome());
            LOGGER.error(
                    "Audit event could not be consumed (action={}, outcome={})",
                    event.action(),
                    event.outcome(),
                    consumeFailure);
        } finally {
            metrics.consumerDuration(event.outcome(), System.nanoTime() - startedAt);
        }
    }

    /** Records queued, consumed, failed-stage, and duration measurements without coupling the listener to Micrometer. */
    interface Metrics {
        Metrics NOOP = new Metrics() {
            @Override
            public void queued(AuditEvent.Outcome outcome) {}

            @Override
            public void consumed(AuditEvent.Outcome outcome) {}

            @Override
            public void failed(String stage, AuditEvent.Outcome outcome) {}

            @Override
            public void consumerDuration(AuditEvent.Outcome outcome, long durationNanos) {}
        };

        void queued(AuditEvent.Outcome outcome);

        void consumed(AuditEvent.Outcome outcome);

        void failed(String stage, AuditEvent.Outcome outcome);

        void consumerDuration(AuditEvent.Outcome outcome, long durationNanos);
    }
}
