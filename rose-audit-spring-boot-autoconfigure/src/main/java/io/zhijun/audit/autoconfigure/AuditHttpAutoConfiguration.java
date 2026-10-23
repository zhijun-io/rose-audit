package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditActorProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.filter.OncePerRequestFilter"})
@ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AuditHttpProperties.class)
public class AuditHttpAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditHttpFilter.class)
    AuditHttpFilter auditHttpFilter(
            AuditHttpProperties properties,
            AuditActorProvider actorProvider,
            AuditEventFactory eventFactory,
            DefaultAuditEventPublisher publisher) {
        return new AuditHttpFilter(properties, actorProvider, eventFactory, publisher);
    }

    @Bean
    FilterRegistrationBean<AuditHttpFilter> auditHttpFilterRegistration(AuditHttpFilter filter) {
        return AuditHttpFilter.registration(filter);
    }
}
