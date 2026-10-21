package io.zhijun.audit.sample;

import io.zhijun.audit.AuditActorProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Supplies a demonstration actor identity from sample request headers, with a fixed fallback identity. */
@Configuration(proxyBeanMethods = false)
public class DemoAuditConfiguration {
    @Bean
    AuditActorProvider demoAuditActorProvider() {
        return new AuditActorProvider() {
            @Override
            public String currentActorId() {
                if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                    HttpServletRequest request = attributes.getRequest();
                    String actor = request.getHeader("X-Demo-Actor");
                    if (actor != null && !actor.isBlank()) {
                        return actor;
                    }
                }
                return "demo-user";
            }

            @Override
            public String currentActorName() {
                if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                    return attributes.getRequest().getHeader("X-Demo-Actor-Name");
                }
                return null;
            }
        };
    }
}
