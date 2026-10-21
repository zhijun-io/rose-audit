package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditCorrelationContextProvider;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditRequestContext;
import io.zhijun.audit.AuditRequestContextProvider;
import io.zhijun.audit.AuditTenantProvider;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Builds immutable audit-event snapshots by combining explicit actor and operation values with tenant, correlation,
 * and request context providers. It normalizes missing values and bounds string fields before publication so values
 * can be safely stored in the starter's schema.
 */
final class AuditEventFactory {
    private static final int MAX_FIELD_LENGTH = 512;

    private final AuditTenantProvider tenantProvider;
    private final AuditCorrelationContextProvider correlationContextProvider;
    private final ObjectProvider<AuditRequestContextProvider> requestContextProvider;

    AuditEventFactory(
            AuditTenantProvider tenantProvider,
            AuditCorrelationContextProvider correlationContextProvider,
            ObjectProvider<AuditRequestContextProvider> requestContextProvider) {
        this.tenantProvider = tenantProvider;
        this.correlationContextProvider = correlationContextProvider;
        this.requestContextProvider = requestContextProvider;
    }

    AuditEvent create(
            String actorId,
            String actorName,
            String action,
            AuditEvent.Outcome outcome,
            long durationMs,
            Throwable failure) {
        AuditCorrelationContext correlation = correlationContextProvider.currentContext();
        if (correlation == null) {
            correlation = AuditCorrelationContext.empty();
        }

        AuditRequestContextProvider provider = requestContextProvider.getIfAvailable();
        AuditRequestContext request = provider == null ? AuditRequestContext.empty() : provider.currentContext();
        if (request == null) {
            request = AuditRequestContext.empty();
        }

        return new AuditEvent(
                Instant.now(),
                optional(tenantProvider.getTenantId()),
                optional(correlation.requestId()),
                optional(correlation.traceId()),
                optional(actorId),
                optional(actorName),
                limit(action),
                outcome,
                durationMs,
                failure == null ? null : limit(failure.getClass().getName()),
                failure == null ? null : limit(failure.getMessage()),
                optional(request.httpMethod()),
                optional(request.url()),
                optional(request.queryString()),
                httpStatus(request.httpStatus(), outcome),
                optional(request.clientIp()),
                optional(request.userAgent()));
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return limit(value);
    }

    private static Integer httpStatus(Integer status, AuditEvent.Outcome outcome) {
        // A Controller exception is published before MVC's exception resolvers set the final response status.
        return outcome == AuditEvent.Outcome.FAILURE && Integer.valueOf(200).equals(status) ? null : status;
    }

    private static String limit(String value) {
        if (value == null) {
            return "";
        }
        String safeValue = value.replace('\n', ' ').replace('\r', ' ');
        return safeValue.length() <= MAX_FIELD_LENGTH ? safeValue : safeValue.substring(0, MAX_FIELD_LENGTH);
    }
}
