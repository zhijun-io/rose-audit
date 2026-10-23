package io.zhijun.audit.autoconfigure;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Copies the principal from a standard logout event onto the open HTTP audit snapshot. */
final class AuditLogoutHttpActorListener {
    @EventListener
    void rememberActor(LogoutSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        String actorName = authentication.getName();
        if (actorName == null || actorName.isBlank()) {
            return;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return;
        }
        if (attributes.getRequest().getAttribute(AuditHttpState.ATTRIBUTE) instanceof AuditHttpState state) {
            state.fillActorIfAbsent(null, actorName);
        }
    }
}
