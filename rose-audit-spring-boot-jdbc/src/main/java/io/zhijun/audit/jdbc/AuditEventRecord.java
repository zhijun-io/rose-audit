package io.zhijun.audit.jdbc;

import io.zhijun.audit.AuditEvent.Outcome;
import io.zhijun.audit.AuditEvent.Type;
import java.time.Instant;

/**
 * Immutable row returned by audit-event searches, including the event identifier and type assigned by the audit core.
 */
public record AuditEventRecord(
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
        String userAgent) {}
