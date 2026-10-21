package io.zhijun.audit.jdbc;

import io.zhijun.audit.AuditEventConsumer;
import io.zhijun.audit.autoconfigure.AuditAutoConfiguration;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Configures JDBC audit persistence when a {@link DataSource} is available. The default {@link AuditEventConsumer}
 * delegates event creation to {@link AuditEventService}; an application-provided consumer replaces it while the JDBC
 * query service remains available. The default consumer calls the service only after the business transaction
 * completes; the service writes the event in a separate transaction. Supplying a custom consumer replaces only
 * consumption and does not disable JDBC queries.
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
    AuditEventConsumer jdbcAuditEventConsumer(AuditEventService auditEventService) {
        return auditEventService::create;
    }
}
