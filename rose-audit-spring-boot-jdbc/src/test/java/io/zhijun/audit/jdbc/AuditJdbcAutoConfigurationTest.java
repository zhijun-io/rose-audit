package io.zhijun.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditEventConsumer;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataRetrievalFailureException;
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
    void failsStartupWhenTheAuditTableIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString()))
                .thenThrow(new DataRetrievalFailureException("relation audit_event does not exist"));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AuditJdbcAutoConfiguration.class))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(JdbcTemplate.class, () -> jdbcTemplate)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("audit_event table was not found");
                });
    }

    @Test
    void skipsSchemaValidationWhenDisabled() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString()))
                .thenThrow(new DataRetrievalFailureException("relation audit_event does not exist"));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AuditJdbcAutoConfiguration.class))
                .withPropertyValues("rose.audit.jdbc.validate-schema=false")
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(JdbcTemplate.class, () -> jdbcTemplate)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditEventService.class);
                });
    }

    @Test
    void keepsSearchAndSkipsTheDefaultConsumerWhenAuditIsDisabled() {
        contextRunner.withPropertyValues("rose.audit.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(AuditEventService.class);
            assertThat(context).doesNotHaveBean(AuditEventConsumer.class);
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
