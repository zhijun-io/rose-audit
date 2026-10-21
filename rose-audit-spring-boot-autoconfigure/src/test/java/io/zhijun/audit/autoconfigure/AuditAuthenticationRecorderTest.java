package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditRequestContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;

/** Verifies authentication recorder events capture the supplied actor identity and result. */
class AuditAuthenticationRecorderTest {
    @Test
    void doesNotPropagatePublisherFailureWhenRecordingSuccess() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        RuntimeException publishFailure = new RuntimeException("database unavailable");
        doThrow(publishFailure).when(events).publishEvent(any(AuditEvent.class));
        DefaultAuditEventPublisher publisher = new DefaultAuditEventPublisher(events);
        var recorder = new AuditAuthenticationRecorder(publisher, eventFactory());

        recorder.recordSuccess("auth.login.success", "user-1", null, new MockHttpServletRequest());
    }

    @Test
    void keepsAuthenticationFailureIndependentFromAuditFailure() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        RuntimeException publishFailure = new RuntimeException("database unavailable");
        doThrow(publishFailure).when(events).publishEvent(any(AuditEvent.class));
        DefaultAuditEventPublisher publisher = new DefaultAuditEventPublisher(events);
        var recorder = new AuditAuthenticationRecorder(publisher, eventFactory());
        var authenticationFailure = new IllegalArgumentException("invalid credentials");

        assertThatCode(() -> recorder.recordFailure(
                        "auth.login.failure", "unknown-user", authenticationFailure, new MockHttpServletRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void publishesSuccessWithCallerMetadataAndConsumesAttemptTimer() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        var recorder = new AuditAuthenticationRecorder(new DefaultAuditEventPublisher(events), eventFactory());
        var request = new MockHttpServletRequest();
        recorder.startAttempt(request);

        recorder.recordSuccess("auth.login.success", "user-1", "Alice", request);
        recorder.recordSuccess("auth.login.success", "user-1", "Alice", request);

        var event = forClass(AuditEvent.class);
        verify(events, org.mockito.Mockito.times(2)).publishEvent(event.capture());
        assertThat(event.getAllValues())
                .extracting(AuditEvent::actorId, AuditEvent::actorName, AuditEvent::action)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("user-1", "Alice", "auth.login.success"),
                        org.assertj.core.groups.Tuple.tuple("user-1", "Alice", "auth.login.success"));
        assertThat(event.getAllValues()).allSatisfy(item -> {
            assertThat(item.outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
            assertThat(item.errorType()).isNull();
        });
        assertThat(event.getAllValues().get(1).durationMs()).isZero();
    }

    @Test
    void publishesFailureWithAttemptedUsernameAndExceptionType() {
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        var recorder = new AuditAuthenticationRecorder(new DefaultAuditEventPublisher(events), eventFactory());
        var failure = new IllegalArgumentException("invalid credentials");

        recorder.recordFailure("auth.login.failure", "unknown-user", failure, new MockHttpServletRequest());

        var event = forClass(AuditEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue())
                .extracting(AuditEvent::actorId, AuditEvent::action, AuditEvent::outcome, AuditEvent::errorType)
                .containsExactly(
                        "unknown-user",
                        "auth.login.failure",
                        AuditEvent.Outcome.FAILURE,
                        IllegalArgumentException.class.getName());
        assertThat(event.getValue().durationMs()).isZero();
    }

    private AuditEventFactory eventFactory() {
        var beanFactory = new DefaultListableBeanFactory();
        return new AuditEventFactory(
                () -> null,
                AuditCorrelationContext::empty,
                beanFactory.getBeanProvider(AuditRequestContextProvider.class));
    }
}
