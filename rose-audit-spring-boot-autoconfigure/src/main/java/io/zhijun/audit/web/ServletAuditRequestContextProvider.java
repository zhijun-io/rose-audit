package io.zhijun.audit.web;

import io.zhijun.audit.AuditRequestContext;
import io.zhijun.audit.AuditRequestContextProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Reads servlet request metadata. Controller response status is left empty because MVC may not have applied the
 * final status yet. The query string is omitted unless explicitly enabled.
 */
public class ServletAuditRequestContextProvider implements AuditRequestContextProvider {
    private final Set<String> queryParametersAllowlist;

    public ServletAuditRequestContextProvider() {
        this(List.of());
    }

    public ServletAuditRequestContextProvider(List<String> queryParametersAllowlist) {
        this.queryParametersAllowlist = queryParametersAllowlist == null
                ? Set.of()
                : queryParametersAllowlist.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .collect(Collectors.toUnmodifiableSet());
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
                filteredQueryString(request.getQueryString()),
                response == null || controllerHandler ? null : response.getStatus(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private String filteredQueryString(String queryString) {
        if (queryString == null || queryParametersAllowlist.isEmpty()) return null;
        var kept = new java.util.ArrayList<String>();
        for (String pair : queryString.split("&")) {
            String key = pair.substring(0, pair.indexOf('=') < 0 ? pair.length() : pair.indexOf('='));
            try {
                if (queryParametersAllowlist.contains(URLDecoder.decode(key, StandardCharsets.UTF_8))) kept.add(pair);
            } catch (IllegalArgumentException malformed) {
                // Malformed parameter names are not retained.
            }
        }
        if (kept.isEmpty()) return null;
        String result = String.join("&", kept);
        return result.length() > 2048 ? result.substring(0, 2048) : result;
    }
}
