package io.zhijun.audit.jdbc;

import io.zhijun.audit.AuditEventConsumer;
import io.zhijun.audit.autoconfigure.AuditAutoConfiguration;
import javax.sql.DataSource;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configures JDBC audit persistence when a {@link DataSource} is available. The default {@link AuditEventConsumer}
 * delegates event creation to {@link AuditEventService}; an application-provided consumer replaces it while the JDBC
 * query service remains available. Execution-fact events are consumed independently of the business transaction; the
 * service writes each event in a separate transaction. Supplying a custom consumer replaces only
 * consumption and does not disable JDBC queries. {@code rose.audit.enabled=false} skips the default consumer and leaves
 * search available.
 */
@AutoConfiguration(
        after = {DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class},
        before = AuditAutoConfiguration.class)
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean(DataSource.class)
public class AuditJdbcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditEventService.class)
    AuditEventService auditEventService(JdbcTemplate jdbcTemplate) {
        return new JdbcAuditEventService(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AuditEventConsumer.class)
    @ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
    AuditEventConsumer jdbcAuditEventConsumer(AuditEventService auditEventService) {
        return auditEventService::create;
    }

    /** Fails startup when the built-in JDBC store is active and {@code audit_event} cannot be queried. */
    @Bean
    @ConditionalOnProperty(prefix = "rose.audit.jdbc", name = "validate-schema", matchIfMissing = true)
    SmartInitializingSingleton auditSchemaValidator(AuditEventService auditEventService, JdbcTemplate jdbcTemplate) {
        return () -> {
            if (!JdbcAuditEventService.class.isAssignableFrom(AopProxyUtils.ultimateTargetClass(auditEventService))) {
                return;
            }
            try {
                jdbcTemplate.queryForList("SELECT event_id, event_type, duration_ms FROM audit_event WHERE 1 = 0");
            } catch (DataAccessException exception) {
                throw new IllegalStateException(
                        "Could not query the current audit_event schema. Apply classpath:db/audit-schema-postgresql.sql to a new database or classpath:db/audit-schema-postgresql-v2.sql to an existing database before starting, or set rose.audit.jdbc.validate-schema=false.",
                        exception);
            }
        };
    }
}
