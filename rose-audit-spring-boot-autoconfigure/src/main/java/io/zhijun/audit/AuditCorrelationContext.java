package io.zhijun.audit;

/** Application request and distributed trace identifiers associated with an audit event. */
public record AuditCorrelationContext(String requestId, String traceId) {
    public static AuditCorrelationContext empty() {
        return new AuditCorrelationContext(null, null);
    }
}
