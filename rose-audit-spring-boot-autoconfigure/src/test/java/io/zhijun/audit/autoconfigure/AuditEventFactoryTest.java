package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditRequestContext;
import io.zhijun.audit.AuditRequestContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/** Verifies event snapshot construction, optional context handling, and field-length normalization. */
class AuditEventFactoryTest {
    @Test
    void capturesContextAndNormalizesBoundedFieldValues() {
        var beanFactory = new DefaultListableBeanFactory();
        String longUserAgent = "agent".repeat(200);
        beanFactory.registerSingleton(
                "requestContextProvider", (AuditRequestContextProvider) () -> new AuditRequestContext(
                        "POST", "/users/{id}", "active=true", 201, "192.0.2.10\nforged", longUserAgent));
        var factory = new AuditEventFactory(
                () -> "tenant-1",
                () -> new AuditCorrelationContext("request-1", "trace-1"),
                beanFactory.getBeanProvider(AuditRequestContextProvider.class));

        AuditEvent event =
                factory.create("actor-1", "Alice", "user.update\nforged", AuditEvent.Outcome.SUCCESS, 123L, null);

        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.requestId()).isEqualTo("request-1");
        assertThat(event.traceId()).isEqualTo("trace-1");
        assertThat(event.actorId()).isEqualTo("actor-1");
        assertThat(event.actorName()).isEqualTo("Alice");
        assertThat(event.action()).isEqualTo("user.update forged");
        assertThat(event.durationMs()).isEqualTo(123L);
        assertThat(event.clientIp()).isEqualTo("192.0.2.10 forged");
        assertThat(event.userAgent()).hasSize(512);
        assertThat(event.httpMethod()).isEqualTo("POST");
        assertThat(event.url()).isEqualTo("/users/{id}");
        assertThat(event.queryString()).isEqualTo("active=true");
        assertThat(event.httpStatus()).isEqualTo(201);
    }

    @Test
    void usesEmptyContextValuesWhenProvidersAreMissingOrReturnNull() {
        var beanFactory = new DefaultListableBeanFactory();
        var factory = new AuditEventFactory(
                () -> null, () -> null, beanFactory.getBeanProvider(AuditRequestContextProvider.class));

        AuditEvent event = factory.create(
                null, null, "action", AuditEvent.Outcome.FAILURE, 0L, new IllegalStateException("failed"));

        assertThat(event.tenantId()).isNull();
        assertThat(event.requestId()).isNull();
        assertThat(event.traceId()).isNull();
        assertThat(event.actorId()).isNull();
        assertThat(event.actorName()).isNull();
        assertThat(event.clientIp()).isNull();
        assertThat(event.userAgent()).isNull();
        assertThat(event.errorMessage()).isEqualTo("failed");
    }

    @Test
    void omitsDefaultOkStatusWhenTheOperationFailed() {
        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("requestContextProvider", (AuditRequestContextProvider)
                () -> new AuditRequestContext("GET", "/users", null, 200, null, null));
        var factory = new AuditEventFactory(
                () -> null, () -> null, beanFactory.getBeanProvider(AuditRequestContextProvider.class));

        AuditEvent event = factory.create(
                null, null, "action", AuditEvent.Outcome.FAILURE, 1L, new IllegalStateException("failed"));

        assertThat(event.httpStatus()).isNull();
    }
}
