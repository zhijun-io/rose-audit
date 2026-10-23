package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditCorrelationContextProvider;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditRequestContext;
import io.zhijun.audit.AuditRequestContextProvider;
import io.zhijun.audit.AuditTenantProvider;
import java.time.Instant;
import java.util.UUID;
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
    private final boolean captureErrorMessage;

    AuditEventFactory(
            AuditTenantProvider tenantProvider,
            AuditCorrelationContextProvider correlationContextProvider,
            ObjectProvider<AuditRequestContextProvider> requestContextProvider,
            boolean captureErrorMessage) {
        this.tenantProvider = tenantProvider;
        this.correlationContextProvider = correlationContextProvider;
        this.requestContextProvider = requestContextProvider;
        this.captureErrorMessage = captureErrorMessage;
    }

    AuditEvent create(
            AuditEvent.Type type,
            String actorId,
            String actorName,
            String action,
            AuditEvent.Outcome outcome,
            Long durationMs,
            Throwable failure) {
        return create(type, actorId, actorName, action, outcome, Instant.now(), durationMs, failure);
    }

    AuditEvent create(
            AuditEvent.Type type,
            String actorId,
            String actorName,
            String action,
            AuditEvent.Outcome outcome,
            Instant occurredAt,
            Long durationMs,
            Throwable failure) {
        AuditCorrelationContext correlation = correlationContextProvider.currentContext();
        if (correlation == null) {
            correlation = AuditCorrelationContext.empty();
        }

        AuditRequestContextProvider provider = requestContextProvider.getIfAvailable();
        boolean background = type == AuditEvent.Type.SCHEDULED_TASK || type == AuditEvent.Type.MESSAGE;
        AuditRequestContext request =
                background || provider == null ? AuditRequestContext.empty() : provider.currentContext();
        if (request == null) {
            request = AuditRequestContext.empty();
        }

        return new AuditEvent(
                UUID.randomUUID().toString(),
                type,
                occurredAt,
                optional(tenantProvider.getTenantId()),
                background ? null : optional(correlation.requestId()),
                optional(correlation.traceId()),
                optional(actorId),
                optional(actorName),
                limit(action),
                outcome,
                durationMs,
                failure == null ? null : limit(failure.getClass().getName()),
                failure == null || !captureErrorMessage || type == AuditEvent.Type.AUTHENTICATION
                        ? null
                        : limit(failure.getMessage()),
                optional(request.httpMethod()),
                optional(request.url()),
                optional(request.queryString()),
                request.httpStatus(),
                optional(request.clientIp()),
                optional(request.userAgent()));
    }

    AuditEvent createHttp(
            AuditEvent context,
            String action,
            AuditEvent.Outcome outcome,
            Instant startedAt,
            long durationMs,
            Throwable failure,
            Integer status) {
        return new AuditEvent(
                UUID.randomUUID().toString(),
                AuditEvent.Type.HTTP_REQUEST,
                startedAt,
                context.tenantId(),
                context.requestId(),
                context.traceId(),
                context.actorId(),
                context.actorName(),
                limit(action),
                outcome,
                durationMs,
                failure == null ? null : limit(failure.getClass().getName()),
                failure == null || !captureErrorMessage ? null : limit(failure.getMessage()),
                context.httpMethod(),
                context.url(),
                context.queryString(),
                status,
                context.clientIp(),
                context.userAgent());
    }

    AuditEvent degradedHttpSnapshot(String requestId, String method, String route, String clientIp, String userAgent) {
        return new AuditEvent(
                UUID.randomUUID().toString(),
                AuditEvent.Type.HTTP_REQUEST,
                Instant.now(),
                null,
                optional(requestId),
                null,
                null,
                null,
                "http.request",
                AuditEvent.Outcome.UNKNOWN,
                null,
                null,
                null,
                optional(method),
                optional(route),
                null,
                null,
                optional(clientIp),
                optional(userAgent));
    }

    AuditEvent snapshotHttp(
            String requestId,
            String actorId,
            String actorName,
            String method,
            String route,
            String clientIp,
            String userAgent) {
        AuditCorrelationContext correlation = correlationContextProvider.currentContext();
        if (correlation == null) correlation = AuditCorrelationContext.empty();
        AuditRequestContextProvider provider = requestContextProvider.getIfAvailable();
        AuditRequestContext request = provider == null ? AuditRequestContext.empty() : provider.currentContext();
        if (request == null) request = AuditRequestContext.empty();
        return new AuditEvent(
                UUID.randomUUID().toString(),
                AuditEvent.Type.HTTP_REQUEST,
                Instant.now(),
                optional(tenantProvider.getTenantId()),
                optional(correlation.requestId() == null ? requestId : correlation.requestId()),
                optional(correlation.traceId()),
                optional(actorId),
                optional(actorName),
                "http.request",
                AuditEvent.Outcome.UNKNOWN,
                0L,
                null,
                null,
                optional(method),
                optional(route),
                optional(request.queryString()),
                null,
                optional(clientIp),
                optional(userAgent));
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return limit(value);
    }

    private static String limit(String value) {
        if (value == null) {
            return "";
        }
        var sanitized = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\n' || codePoint == '\r') {
                sanitized.append(' ');
            } else if (!Character.isISOControl(codePoint)) {
                sanitized.appendCodePoint(codePoint);
            }
        }
        String safeValue = sanitized.toString();
        return safeValue.length() <= MAX_FIELD_LENGTH ? safeValue : safeValue.substring(0, MAX_FIELD_LENGTH);
    }
}
