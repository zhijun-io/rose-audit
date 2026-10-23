package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditEventConsumer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    void enqueuesImmediatelyEvenInsideABusinessTransaction() {
        beginTransaction();
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);

        listener.consume(event);
        assertThatTaskQueued(event);
        runQueuedTask();

        verify(consumer).consume(event);
    }

    @Test
    void preservesSuccessFactWhenBusinessTransactionRollsBack() {
        beginTransaction();
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);

        listener.consume(event);
        assertThatTaskQueued(event);
        runQueuedTask();
        verify(consumer).consume(event);
    }

    @Test
    void preservesFailureFactImmediatelyInsideATransaction() {
        beginTransaction();
        AuditEvent event = event(AuditEvent.Outcome.FAILURE);

        listener.consume(event);
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

    @Test
    void defaultExecutorWaitsForQueuedAuditWorkDuringShutdown() throws Exception {
        ThreadPoolTaskExecutor executor = new AuditAutoConfiguration().auditEventTaskExecutor();
        executor.initialize();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                release.await();
                completed.countDown();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean shutdownReturned = new AtomicBoolean();
        Thread shutdown = new Thread(() -> {
            executor.destroy();
            shutdownReturned.set(true);
        });
        shutdown.start();
        try {
            await().atMost(Duration.ofSeconds(1))
                    .untilAsserted(() -> assertThat(shutdown.isAlive()).isTrue());
            assertThat(shutdownReturned).isFalse();
        } finally {
            release.countDown();
        }
        shutdown.join(2_000);
        assertThat(shutdownReturned).isTrue();
        assertThat(completed.getCount()).isZero();
    }

    @Test
    void limitsRepeatedFailureLogsAndReportsSuppressedCount() {
        AtomicLong now = new AtomicLong(1);
        var limiter = new AuditEventListener.FailureLogLimiter(now::get);

        assertThat(limiter.shouldLog()).isTrue();
        now.addAndGet(TimeUnit.SECONDS.toNanos(9));
        assertThat(limiter.shouldLog()).isFalse();
        assertThat(limiter.shouldLog()).isFalse();
        assertThat(limiter.takeSuppressed()).isEqualTo(2);
        now.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertThat(limiter.shouldLog()).isTrue();
        assertThat(limiter.takeSuppressed()).isZero();
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void assertThatTaskQueued(AuditEvent event) {
        assertThat(queuedTasks).as("task queued for %s", event).hasSize(1);
    }

    private void runQueuedTask() {
        queuedTasks.removeFirst().run();
    }

    private AuditEvent event(AuditEvent.Outcome outcome) {
        return new AuditEvent(
                "test-event",
                AuditEvent.Type.OPERATION,
                Instant.parse("2026-10-21T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                "test.action",
                outcome,
                1L,
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
