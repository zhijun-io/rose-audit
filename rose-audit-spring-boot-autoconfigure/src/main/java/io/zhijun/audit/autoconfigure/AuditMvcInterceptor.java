package io.zhijun.audit.autoconfigure;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

final class AuditMvcInterceptor implements HandlerInterceptor {
    private final AuditHttpFilter filter;

    AuditMvcInterceptor(AuditHttpFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() == DispatcherType.ERROR
                || request.getDispatcherType() == DispatcherType.INCLUDE) {
            return true;
        }
        if (handler instanceof HandlerMethod method) {
            Object routeValue = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (routeValue instanceof String route) {
                filter.captureController(request, request.getMethod() + " " + route);
            }
        }
        return true;
    }
}
