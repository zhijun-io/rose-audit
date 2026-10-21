package io.zhijun.audit;

import java.time.Instant;

/** Immutable snapshot of an audited operation and the request context available when it ran. */
public record AuditEvent(
        Instant occurredAt,
        String tenantId,
        String requestId,
        String traceId,
        String actorId,
        String actorName,
        String action,
        Outcome outcome,
        long durationMs,
        String errorType,
        String errorMessage,
        String httpMethod,
        String url,
        String queryString,
        Integer httpStatus,
        String clientIp,
        String userAgent) {
    public enum Outcome {
        SUCCESS,
        FAILURE
    }
}
