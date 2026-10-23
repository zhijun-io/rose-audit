package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditRequestContextProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

class AuditSecurityEventListenerTest {
    @Test
    void recordsStandardAuthenticationEventsWithoutCapturingFailedCredentials() {
        List<AuditEvent> published = new ArrayList<>();
        ApplicationEventPublisher springEvents = event -> published.add((AuditEvent) event);
        var listener = new AuditSecurityEventListener(new DefaultAuditEventPublisher(springEvents), eventFactory(true));
        var authentication = new TestingAuthenticationToken("alice", "secret", "ROLE_USER");

        listener.recordSuccess(new AuthenticationSuccessEvent(authentication));
        listener.recordFailure(new AuthenticationFailureBadCredentialsEvent(
                new TestingAuthenticationToken("unknown-user", "secret"),
                new BadCredentialsException("password rejected")));

        assertThat(published).hasSize(2);
        assertThat(published.getFirst())
                .extracting(AuditEvent::type, AuditEvent::actorName, AuditEvent::outcome, AuditEvent::durationMs)
                .containsExactly(AuditEvent.Type.AUTHENTICATION, "alice", AuditEvent.Outcome.SUCCESS, null);
        assertThat(published.get(1))
                .extracting(
                        AuditEvent::type,
                        AuditEvent::actorId,
                        AuditEvent::actorName,
                        AuditEvent::outcome,
                        AuditEvent::errorMessage,
                        AuditEvent::durationMs)
                .containsExactly(AuditEvent.Type.AUTHENTICATION, null, null, AuditEvent.Outcome.FAILURE, null, null);
    }

    @Test
    void suppressesContextProviderFailureInsteadOfChangingAuthentication() {
        var beanFactory = new DefaultListableBeanFactory();
        var factory = new AuditEventFactory(
                () -> null,
                () -> {
                    throw new IllegalStateException("provider failure");
                },
                beanFactory.getBeanProvider(AuditRequestContextProvider.class),
                false);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        var listener = new AuditSecurityEventListener(new DefaultAuditEventPublisher(events), factory);

        listener.recordSuccess(
                new AuthenticationSuccessEvent(new TestingAuthenticationToken("alice", "secret", "ROLE_USER")));

        verify(events, org.mockito.Mockito.never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    private AuditEventFactory eventFactory(boolean captureMessage) {
        var beans = new DefaultListableBeanFactory();
        return new AuditEventFactory(
                () -> null,
                AuditCorrelationContext::empty,
                beans.getBeanProvider(AuditRequestContextProvider.class),
                captureMessage);
    }
}
