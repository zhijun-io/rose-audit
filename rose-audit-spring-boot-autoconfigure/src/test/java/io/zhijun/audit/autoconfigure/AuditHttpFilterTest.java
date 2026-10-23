package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.zhijun.audit.AuditActorProvider;
import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditRequestContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;

class AuditHttpFilterTest {
    @Test
    void keepsTheRequestIdAndCountsAFailedContextCapture() {
        var metrics = mock(AuditEventListener.Metrics.class);
        var publisher = new DefaultAuditEventPublisher(event -> {}, metrics);
        var beans = new DefaultListableBeanFactory();
        var factory = new AuditEventFactory(
                () -> null,
                AuditCorrelationContext::empty,
                beans.getBeanProvider(AuditRequestContextProvider.class),
                false);
        AuditActorProvider actors = new AuditActorProvider() {
            @Override
            public String currentActorId() {
                throw new IllegalStateException("actor unavailable");
            }

            @Override
            public String currentActorName() {
                return null;
            }
        };
        var filter = new AuditHttpFilter(new AuditHttpProperties(), actors, factory, publisher);
        var request = new MockHttpServletRequest("GET", "/orders/15");
        var state = new AuditHttpState();
        request.setAttribute(AuditHttpState.ATTRIBUTE, state);

        filter.captureController(request, "GET /orders/{id}");

        assertThat(state.snapshot().requestId()).isEqualTo(state.requestId());
        assertThat(state.snapshot().action()).isEqualTo("http.request");
        verify(metrics).failed("build", AuditEvent.Outcome.UNKNOWN);
    }

    @Test
    void keepsCommittedOkStatusWhenTheTransferFailedAndDropsAnUncommittedDefaultOk() {
        var failure = new java.io.IOException("write failed");

        assertThat(AuditHttpFilter.observedStatus(200, true, failure)).isEqualTo(200);
        assertThat(AuditHttpFilter.outcome(200, failure)).isEqualTo(AuditEvent.Outcome.FAILURE);
        assertThat(AuditHttpFilter.observedStatus(200, false, failure)).isNull();
        assertThat(AuditHttpFilter.outcome(null, failure)).isEqualTo(AuditEvent.Outcome.FAILURE);
        assertThat(AuditHttpFilter.outcome(204, null)).isEqualTo(AuditEvent.Outcome.SUCCESS);
    }
}
