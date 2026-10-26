package io.zhijun.audit.jdbc;

import io.zhijun.audit.AuditEvent;
import java.time.Instant;

/**
 * Optional exact-match criteria for an audit search. The {@code from} bound is inclusive and {@code to} is exclusive;
 * null or blank string criteria are ignored by the JDBC implementation. Pagination and sorting are supplied
 * separately as a Spring Data {@link org.springframework.data.domain.Pageable}.
 */
public record AuditEventQueryRequest(
        String tenantId,
        String requestId,
        String traceId,
        String actorId,
        String action,
        AuditEvent.Outcome outcome,
        Instant from,
        Instant to) {
    /** Convenience constructor for searches without tenant or correlation filters. */
    public AuditEventQueryRequest(String actorId, String action, AuditEvent.Outcome outcome, Instant from, Instant to) {
        this(null, null, null, actorId, action, outcome, from, to);
    }

    /** Convenience constructor for searches with tenant scope only. */
    public AuditEventQueryRequest(
            String tenantId, String actorId, String action, AuditEvent.Outcome outcome, Instant from, Instant to) {
        this(tenantId, null, null, actorId, action, outcome, from, to);
    }

    public AuditEventQueryRequest {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }

    public static AuditEventQueryRequest all() {
        return new AuditEventQueryRequest(null, null, null, null, null, null, null, null);
    }
}
