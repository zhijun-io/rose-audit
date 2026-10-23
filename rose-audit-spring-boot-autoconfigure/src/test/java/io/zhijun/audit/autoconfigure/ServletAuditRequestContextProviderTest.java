package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.zhijun.audit.AuditRequestContext;
import io.zhijun.audit.web.ServletAuditRequestContextProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Verifies servlet request metadata extraction and behavior when no request is bound. */
class ServletAuditRequestContextProviderTest {
    private final ServletAuditRequestContextProvider provider = new ServletAuditRequestContextProvider();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsEmptyContextWhenNoServletRequestIsBound() {
        assertThat(provider.currentContext()).isEqualTo(AuditRequestContext.empty());
    }

    @Test
    void capturesRemoteAddressAndUserAgentFromBoundRequest() {
        var request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "audit-test-agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));

        assertThat(provider.currentContext())
                .isEqualTo(new AuditRequestContext("GET", "", null, 200, "192.0.2.10", "audit-test-agent"));
    }

    @Test
    void omitsTheQueryStringUnlessCaptureIsEnabled() {
        var request = new MockHttpServletRequest("GET", "/orders");
        request.setQueryString("token=secret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));

        assertThat(provider.currentContext().queryString()).isNull();
        assertThat(new ServletAuditRequestContextProvider(true).currentContext().queryString())
                .isEqualTo("token=secret");
    }
}
