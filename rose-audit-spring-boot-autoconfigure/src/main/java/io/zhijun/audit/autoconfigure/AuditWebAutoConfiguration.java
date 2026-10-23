package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditCorrelationContextProvider;
import io.zhijun.audit.AuditRequestContextProvider;
import io.zhijun.audit.web.ServletAuditRequestContextProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Registers the servlet request metadata provider when Servlet APIs are present and no application provider exists. */
@AutoConfiguration(before = AuditAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(
        name = {
            "org.springframework.web.context.request.RequestContextHolder",
            "jakarta.servlet.http.HttpServletRequest"
        })
@ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
public class AuditWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AuditCorrelationContextProvider.class)
    AuditCorrelationContextProvider servletAuditCorrelationContextProvider() {
        return () -> {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes
                    && servletAttributes.getRequest().getAttribute(AuditHttpState.ATTRIBUTE)
                            instanceof AuditHttpState state) {
                return new AuditCorrelationContext(state.requestId(), null);
            }
            return AuditCorrelationContext.empty();
        };
    }

    @Bean
    @ConditionalOnMissingBean(AuditRequestContextProvider.class)
    AuditRequestContextProvider servletAuditRequestContextProvider(AuditHttpProperties properties) {
        return new ServletAuditRequestContextProvider(properties.getQueryParametersAllowlist());
    }
}
