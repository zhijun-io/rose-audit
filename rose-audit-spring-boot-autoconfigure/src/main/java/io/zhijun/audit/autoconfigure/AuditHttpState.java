package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditEvent;
import java.time.Instant;
import java.util.UUID;

final class AuditHttpState {
    static final String ATTRIBUTE = AuditHttpState.class.getName();

    private final long startedNanos = System.nanoTime();
    private final Instant startedAt = Instant.now();
    private final String requestId = UUID.randomUUID().toString();
    private boolean completed;
    private boolean eligible;
    private Throwable failure;
    private String action;
    private AuditEvent snapshot;
    private boolean controllerCaptured;

    String requestId() {
        return requestId;
    }

    synchronized AuditEvent snapshot() {
        return snapshot;
    }

    synchronized void fail(Throwable failure) {
        if (!completed && this.failure == null && failure != null) {
            this.failure = failure;
        }
    }

    synchronized void include(String action, AuditEvent snapshot) {
        this.eligible = true;
        this.action = action;
        this.snapshot = snapshot;
    }

    synchronized void captureController(String action, AuditEvent snapshot) {
        if (controllerCaptured) {
            return;
        }
        controllerCaptured = true;
        eligible = true;
        this.action = action;
        this.snapshot = snapshot;
    }

    /** Keeps a logout identity when the security context is cleared before the request finishes. */
    synchronized void fillActorIfAbsent(String actorId, String actorName) {
        if (snapshot == null || snapshot.actorId() != null || snapshot.actorName() != null) {
            return;
        }
        if (actorId == null && actorName == null) {
            return;
        }
        snapshot = new AuditEvent(
                snapshot.eventId(),
                snapshot.type(),
                snapshot.occurredAt(),
                snapshot.tenantId(),
                snapshot.requestId(),
                snapshot.traceId(),
                actorId,
                actorName,
                snapshot.action(),
                snapshot.outcome(),
                snapshot.durationMs(),
                snapshot.errorType(),
                snapshot.errorMessage(),
                snapshot.httpMethod(),
                snapshot.url(),
                snapshot.queryString(),
                snapshot.httpStatus(),
                snapshot.clientIp(),
                snapshot.userAgent());
    }

    /**
     * Marks the request complete and returns the facts visible at that instant. A failure recorded earlier is kept.
     * A later failure cannot change the returned decision.
     */
    synchronized Decision claimCompletion() {
        if (!eligible || completed) {
            return null;
        }
        completed = true;
        Decision decision = new Decision(action, snapshot, failure, startedAt, startedNanos);
        snapshot = null;
        action = null;
        failure = null;
        return decision;
    }

    record Decision(String action, AuditEvent snapshot, Throwable failure, Instant startedAt, long startedNanos) {}
}
