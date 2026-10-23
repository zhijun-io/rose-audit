package io.zhijun.audit.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Registers the authentication audit recorder only when Spring Security and Servlet APIs are available. */
@AutoConfiguration
@ConditionalOnClass(
        name = {"org.springframework.security.core.Authentication", "jakarta.servlet.http.HttpServletRequest"})
@ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
public class AuditAuthenticationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    AuditAuthenticationRecorder auditAuthenticationRecorder(
            DefaultAuditEventPublisher auditEventPublisher, AuditEventFactory eventFactory) {
        return new AuditAuthenticationRecorder(auditEventPublisher, eventFactory);
    }
}
