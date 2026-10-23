package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditActorProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Selects the Spring Security principal when the application has not defined its own actor provider. */
@AutoConfiguration(before = AuditAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
@ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
public class AuditSecurityActorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    AuditActorProvider securityAuditActorProvider() {
        return new SecurityContextAuditActorProvider();
    }
}

/**
 * Uses the authenticated principal name as the display name. {@code Authentication.getName()} is not an account id, so
 * the actor id stays empty unless the application supplies an {@link AuditActorProvider}.
 */
final class SecurityContextAuditActorProvider implements AuditActorProvider {
    @Override
    public String currentActorId() {
        return null;
    }

    @Override
    public String currentActorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String actorName = authentication.getName();
        return actorName == null || actorName.isBlank() ? null : actorName;
    }
}
