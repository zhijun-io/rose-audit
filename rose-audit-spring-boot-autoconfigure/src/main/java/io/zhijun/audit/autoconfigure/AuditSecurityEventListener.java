package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

final class AuditSecurityEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditSecurityEventListener.class);
    private final DefaultAuditEventPublisher publisher;
    private final AuditEventFactory eventFactory;

    AuditSecurityEventListener(DefaultAuditEventPublisher publisher, AuditEventFactory eventFactory) {
        this.publisher = publisher;
        this.eventFactory = eventFactory;
    }

    @EventListener
    void recordSuccess(AuthenticationSuccessEvent event) {
        publish("authentication.success", event.getAuthentication(), AuditEvent.Outcome.SUCCESS, null);
    }

    @EventListener
    void recordFailure(AbstractAuthenticationFailureEvent event) {
        publish("authentication.failure", null, AuditEvent.Outcome.FAILURE, event.getException());
    }

    private void publish(String action, Authentication authentication, AuditEvent.Outcome outcome, Throwable failure) {
        try {
            String actorName = authentication == null ? null : authentication.getName();
            publisher.publish(eventFactory.create(
                    AuditEvent.Type.AUTHENTICATION, null, actorName, action, outcome, null, failure));
        } catch (RuntimeException auditFailure) {
            LOGGER.warn(
                    "Authentication audit event could not be built (cause={})",
                    auditFailure.getClass().getName());
        }
    }
}
