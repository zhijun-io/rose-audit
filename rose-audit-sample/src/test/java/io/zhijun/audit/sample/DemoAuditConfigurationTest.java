package io.zhijun.audit.sample;

import static org.assertj.core.api.Assertions.assertThat;

import io.zhijun.audit.AuditActorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class DemoAuditConfigurationTest {
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesFallbackIdentityWithoutAnHttpRequest() {
        AuditActorProvider provider = new DemoAuditConfiguration().demoAuditActorProvider();

        assertThat(provider.currentActorId()).isEqualTo("demo-user");
        assertThat(provider.currentActorName()).isNull();
    }

    @Test
    void readsActorHeadersAndUsesFallbackForBlankActorId() {
        AuditActorProvider provider = new DemoAuditConfiguration().demoAuditActorProvider();
        var request = new MockHttpServletRequest();
        request.addHeader("X-Demo-Actor", "alice");
        request.addHeader("X-Demo-Actor-Name", "Alice Example");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(provider.currentActorId()).isEqualTo("alice");
        assertThat(provider.currentActorName()).isEqualTo("Alice Example");

        request.removeHeader("X-Demo-Actor");
        request.addHeader("X-Demo-Actor", "  ");
        assertThat(provider.currentActorId()).isEqualTo("demo-user");
    }
}
