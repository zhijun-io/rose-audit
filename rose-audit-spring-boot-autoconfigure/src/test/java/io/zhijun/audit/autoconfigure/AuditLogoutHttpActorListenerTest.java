package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.zhijun.audit.AuditEvent;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditLogoutHttpActorListenerTest {
    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void copiesTheLoggedOutPrincipalOntoAnEmptyHttpSnapshot() {
        var request = new MockHttpServletRequest("POST", "/logout");
        var state = new AuditHttpState();
        state.include("POST /logout", emptySnapshot(state.requestId()));
        request.setAttribute(AuditHttpState.ATTRIBUTE, state);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        new AuditLogoutHttpActorListener()
                .rememberActor(new LogoutSuccessEvent(new TestingAuthenticationToken("alice", "secret", "ROLE_USER")));

        assertThat(state.snapshot().actorName()).isEqualTo("alice");
        assertThat(state.snapshot().actorId()).isNull();
        assertThat(state.snapshot().requestId()).isEqualTo(state.requestId());
    }

    private static AuditEvent emptySnapshot(String requestId) {
        return new AuditEvent(
                "event-1",
                AuditEvent.Type.HTTP_REQUEST,
                Instant.EPOCH,
                null,
                requestId,
                null,
                null,
                null,
                "POST /logout",
                AuditEvent.Outcome.UNKNOWN,
                null,
                null,
                null,
                "POST",
                "/logout",
                null,
                null,
                null,
                null);
    }
}
