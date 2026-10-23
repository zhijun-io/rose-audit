package io.zhijun.audit.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AuditHttpAutoConfiguration.class)
@ConditionalOnClass(
        name = {
            "org.springframework.security.authentication.event.LogoutSuccessEvent",
            "org.springframework.web.context.request.RequestContextHolder",
            "jakarta.servlet.http.HttpServletRequest"
        })
@ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
class AuditLogoutHttpActorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditLogoutHttpActorListener.class)
    AuditLogoutHttpActorListener auditLogoutHttpActorListener() {
        return new AuditLogoutHttpActorListener();
    }
}
