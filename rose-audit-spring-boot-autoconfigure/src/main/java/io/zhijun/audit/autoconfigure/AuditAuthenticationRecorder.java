package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Records authentication outcomes from a security filter's success and failure handlers. */
public final class AuditAuthenticationRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditAuthenticationRecorder.class);
    private static final String STARTED_AT_ATTRIBUTE = AuditAuthenticationRecorder.class.getName() + ".startedAtNanos";

    private final DefaultAuditEventPublisher publisher;
    private final AuditEventFactory eventFactory;

    AuditAuthenticationRecorder(DefaultAuditEventPublisher publisher, AuditEventFactory eventFactory) {
        this.publisher = publisher;
        this.eventFactory = eventFactory;
    }

    /** Mark the start of authentication in the request before invoking the AuthenticationManager. */
    public void startAttempt(HttpServletRequest request) {
        request.setAttribute(STARTED_AT_ATTRIBUTE, System.nanoTime());
    }

    /** Record success using the caller-defined action, account id, and display name. */
    public void recordSuccess(String action, String actorId, String actorName, HttpServletRequest request) {
        publish(action, actorId, actorName, AuditEvent.Outcome.SUCCESS, null, durationMs(request));
    }

    /** Record failure with the submitted username as the display name. The account id stays empty. */
    public void recordFailure(String action, String attemptedUsername, Throwable failure, HttpServletRequest request) {
        publish(action, null, attemptedUsername, AuditEvent.Outcome.FAILURE, failure, durationMs(request));
    }

    private void publish(
            String action,
            String actorId,
            String actorName,
            AuditEvent.Outcome outcome,
            Throwable failure,
            long durationMs) {
        try {
            publisher.publish(eventFactory.create(actorId, actorName, action, outcome, durationMs, failure));
        } catch (RuntimeException publishFailure) {
            LOGGER.warn(
                    "Authentication audit event publishing failed (action={}, outcome={})",
                    action,
                    outcome,
                    publishFailure);
        }
    }

    private long durationMs(HttpServletRequest request) {
        Object startedAt = request.getAttribute(STARTED_AT_ATTRIBUTE);
        request.removeAttribute(STARTED_AT_ATTRIBUTE);
        if (startedAt instanceof Long startedAtNanos) {
            return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedAtNanos));
        }
        return 0;
    }
}
