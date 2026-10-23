package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditEventConsumer;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;

/** Verifies failure metrics when consumption throws and the business publish call still returns. */
class AuditMicrometerAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(AuditAutoConfiguration.class, AuditMicrometerAutoConfiguration.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
            .withBean(AuditEventConsumer.class, () -> event -> {
                throw new IllegalStateException("database unavailable");
            })
            .withBean("auditEventTaskExecutor", TaskExecutor.class, () -> Runnable::run);

    @Test
    void countsConsumeFailuresWithoutFailingThePublisher() {
        contextRunner.run(context -> {
            AuditEvent event = new AuditEvent(
                    "test-event",
                    AuditEvent.Type.OPERATION,
                    Instant.parse("2026-10-22T00:00:00Z"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    "order.create",
                    AuditEvent.Outcome.SUCCESS,
                    1L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            assertThatCode(() ->
                            context.getBean(DefaultAuditEventPublisher.class).publish(event))
                    .doesNotThrowAnyException();

            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.find("rose.audit.events.failures")
                            .tag("stage", "consume")
                            .tag("outcome", "success")
                            .counter())
                    .isNotNull()
                    .extracting(counter -> counter.count())
                    .isEqualTo(1.0);
        });
    }
}
