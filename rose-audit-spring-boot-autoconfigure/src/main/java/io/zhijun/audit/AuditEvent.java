package io.zhijun.audit;

import java.time.Instant;
import java.util.Objects;

/** Immutable snapshot of an audited operation and the request context available when it ran. */
public record AuditEvent(
        String eventId,
        Type type,
        Instant occurredAt,
        String tenantId,
        String requestId,
        String traceId,
        String actorId,
        String actorName,
        String action,
        Outcome outcome,
        Long durationMs,
        String errorType,
        String errorMessage,
        String httpMethod,
        String url,
        String queryString,
        Integer httpStatus,
        String clientIp,
        String userAgent) {
    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
    }

    public enum Type {
        HTTP_REQUEST,
        AUTHENTICATION,
        SCHEDULED_TASK,
        MESSAGE,
        OPERATION,
        UNKNOWN
    }

    public enum Outcome {
        SUCCESS,
        FAILURE,
        UNKNOWN
    }
}
