package io.zhijun.audit.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AuditAutoConfiguration.class)
@ConditionalOnClass(
        name = {
            "org.springframework.security.authentication.event.AuthenticationSuccessEvent",
            "org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent"
        })
@ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
class AuditSecurityEventsAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditSecurityEventListener.class)
    AuditSecurityEventListener auditSecurityEventListener(
            DefaultAuditEventPublisher publisher, AuditEventFactory eventFactory) {
        return new AuditSecurityEventListener(publisher, eventFactory);
    }
}
