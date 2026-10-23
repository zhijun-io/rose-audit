package io.zhijun.audit.web;

import io.zhijun.audit.AuditRequestContext;
import io.zhijun.audit.AuditRequestContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Reads servlet request metadata. Controller response status is left empty because MVC may not have applied the
 * final status yet. The query string is omitted unless explicitly enabled.
 */
public class ServletAuditRequestContextProvider implements AuditRequestContextProvider {
    private final boolean captureQueryString;

    public ServletAuditRequestContextProvider() {
        this(false);
    }

    public ServletAuditRequestContextProvider(boolean captureQueryString) {
        this.captureQueryString = captureQueryString;
    }

    @Override
    public AuditRequestContext currentContext() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return AuditRequestContext.empty();
        }

        HttpServletRequest request = attributes.getRequest();
        Object bestMatchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String url = bestMatchingPattern instanceof String pattern ? pattern : request.getRequestURI();
        HttpServletResponse response = attributes.getResponse();
        boolean controllerHandler =
                request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod;
        return new AuditRequestContext(
                request.getMethod(),
                url,
                captureQueryString ? request.getQueryString() : null,
                response == null || controllerHandler ? null : response.getStatus(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }
}
