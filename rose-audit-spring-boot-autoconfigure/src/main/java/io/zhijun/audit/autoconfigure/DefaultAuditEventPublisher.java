package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/** Publishes audit events through Spring's application event mechanism without affecting the business invocation. */
final class DefaultAuditEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger("audit.events");

    private final ApplicationEventPublisher eventPublisher;
    private final AuditEventListener.Metrics metrics;

    DefaultAuditEventPublisher(ApplicationEventPublisher eventPublisher) {
        this(eventPublisher, AuditEventListener.Metrics.NOOP);
    }

    DefaultAuditEventPublisher(ApplicationEventPublisher eventPublisher, AuditEventListener.Metrics metrics) {
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    public void publish(AuditEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (RuntimeException publishFailure) {
            metrics.failed("publish", event.outcome());
            LOGGER.error(
                    "Audit event could not be published (action={}, outcome={})",
                    event.action(),
                    event.outcome(),
                    publishFailure);
        }
    }
}
