package io.zhijun.audit.jdbc;

import io.zhijun.audit.AuditEvent.Outcome;
import java.time.Instant;

/**
 * Immutable row returned by audit-event searches. In addition to the captured event fields it includes the persistence
 * identifier assigned by the JDBC implementation; it is not the input type used to publish a new event.
 */
public record AuditEventRecord(
        String eventId,
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
        String userAgent) {}
