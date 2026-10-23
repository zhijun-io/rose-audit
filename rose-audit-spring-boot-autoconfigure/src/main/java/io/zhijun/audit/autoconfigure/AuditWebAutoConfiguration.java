package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditRequestContextProvider;
import io.zhijun.audit.web.ServletAuditRequestContextProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Registers the servlet request metadata provider when Servlet APIs are present and no application provider exists. */
@AutoConfiguration
@ConditionalOnClass(
        name = {
            "org.springframework.web.context.request.RequestContextHolder",
            "jakarta.servlet.http.HttpServletRequest"
        })
@ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
public class AuditWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditRequestContextProvider.class)
    AuditRequestContextProvider servletAuditRequestContextProvider() {
        return new ServletAuditRequestContextProvider();
    }
}
