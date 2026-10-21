package io.zhijun.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditEventConsumer;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies JDBC persistence and consumer auto-configuration, including custom consumer backoff. */
class AuditJdbcAutoConfigurationTest {
    private final AuditEventService auditEventService = mock(AuditEventService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuditJdbcAutoConfiguration.class))
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(AuditEventService.class, () -> auditEventService);

    @Test
    void providesJdbcConsumerByDefault() {
        contextRunner.run(context -> {
            AuditEventConsumer consumer = context.getBean(AuditEventConsumer.class);
            AuditEvent event = event();

            consumer.consume(event);

            verify(auditEventService).create(event);
        });
    }

    @Test
    void backsOffWhenApplicationProvidesAnotherConsumer() {
        AuditEventConsumer customConsumer = mock(AuditEventConsumer.class);
        contextRunner.withBean(AuditEventConsumer.class, () -> customConsumer).run(context -> {
            assertThat(context.getBean(AuditEventConsumer.class)).isSameAs(customConsumer);
            assertThat(context).hasSingleBean(AuditEventConsumer.class);
        });
    }

    private AuditEvent event() {
        return new AuditEvent(
                Instant.parse("2026-10-21T00:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                "test.action",
                AuditEvent.Outcome.SUCCESS,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
