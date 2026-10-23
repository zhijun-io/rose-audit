package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.zhijun.audit.AuditEvent;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Verifies publishing failures are contained and do not escape the audit publisher. */
class DefaultAuditEventPublisherTest {
    private ApplicationEventPublisher eventPublisher;
    private DefaultAuditEventPublisher publisher;

    @BeforeEach
    void setUp() {
        eventPublisher = mock(ApplicationEventPublisher.class);
        publisher = new DefaultAuditEventPublisher(eventPublisher);
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void publishesImmediatelyWhenNoBusinessTransactionIsActive() {
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);

        publisher.publish(event);

        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void publishesImmediatelyEvenWhenBusinessTransactionIsActive() {
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(event);

        verify(eventPublisher).publishEvent(event);
    }

    @Test
    void doesNotPropagateEventConsumerFailure() {
        AuditEvent event = event(AuditEvent.Outcome.SUCCESS);
        doThrow(new IllegalStateException("consumer unavailable"))
                .when(eventPublisher)
                .publishEvent(event);

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    private AuditEvent event(AuditEvent.Outcome outcome) {
        return new AuditEvent(
                "test-event",
                AuditEvent.Type.OPERATION,
                Instant.parse("2026-10-21T00:00:00Z"),
                "tenant-test",
                "request-test",
                "trace-test",
                "actor-test",
                "Actor",
                "action.test",
                outcome,
                100L,
                outcome == AuditEvent.Outcome.FAILURE ? IllegalStateException.class.getName() : null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
