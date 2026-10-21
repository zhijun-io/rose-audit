package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditEventConsumer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/** Verifies transaction-phase delivery, immediate delivery, and isolation of consumer failures. */
class AuditEventListenerTest {
    private final AuditEventConsumer consumer = mock(AuditEventConsumer.class);
    private final List<Runnable> queuedTasks = new ArrayList<>();
    private final AuditEventListener listener = new AuditEventListener(consumer, (TaskExecutor) queuedTasks::add);

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void enqueuesSuccessOnlyAfterCommit() {
        beginTransaction();
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);

        listener.consume(event);
        assertNoTaskQueued(event);

        TransactionSynchronizationUtils.triggerAfterCommit();
        assertThatTaskQueued(event);
        runQueuedTask();

        verify(consumer).consume(event);
    }

    @Test
    void doesNotEnqueueSuccessWhenTransactionRollsBack() {
        beginTransaction();
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);

        listener.consume(event);
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertNoTaskQueued(event);
        verify(consumer, never()).consume(event);
    }

    @Test
    void enqueuesFailureAfterTransactionCompletionOnRollback() {
        beginTransaction();
        AuditEvent event = event(AuditEvent.Outcome.FAILURE);

        listener.consume(event);
        assertNoTaskQueued(event);

        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThatTaskQueued(event);
        runQueuedTask();

        verify(consumer).consume(event);
    }

    @Test
    void enqueuesImmediatelyWithoutAnActiveTransaction() {
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);

        listener.consume(event);
        assertThatTaskQueued(event);
        runQueuedTask();

        verify(consumer).consume(event);
    }

    @Test
    void doesNotPropagateConsumerFailureFromWorkerTask() {
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);
        doThrow(new IllegalStateException("consumer unavailable"))
                .when(consumer)
                .consume(event);
        listener.consume(event);

        assertThatCode(this::runQueuedTask).doesNotThrowAnyException();
    }

    @Test
    void logsAndSuppressesQueueRejection() {
        TaskExecutor rejectingExecutor = task -> {
            throw new IllegalStateException("queue full");
        };
        AuditEventListener rejectingListener = new AuditEventListener(consumer, rejectingExecutor);
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);

        assertThatCode(() -> rejectingListener.consume(event)).doesNotThrowAnyException();

        verify(consumer, never()).consume(event);
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void assertNoTaskQueued(AuditEvent event) {
        assertThat(queuedTasks).as("no task queued for %s", event).isEmpty();
    }

    private void assertThatTaskQueued(AuditEvent event) {
        assertThat(queuedTasks).as("task queued for %s", event).hasSize(1);
    }

    private void runQueuedTask() {
        queuedTasks.removeFirst().run();
    }

    private AuditEvent event(AuditEvent.Outcome outcome) {
        return new AuditEvent(
                Instant.parse("2026-10-21T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                "test.action",
                outcome,
                1,
                outcome == AuditEvent.Outcome.FAILURE ? "java.lang.IllegalStateException" : null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
