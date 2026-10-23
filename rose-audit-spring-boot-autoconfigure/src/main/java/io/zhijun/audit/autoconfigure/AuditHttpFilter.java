package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditActorProvider;
import io.zhijun.audit.AuditEvent;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

final class AuditHttpFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditHttpFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private final AuditHttpProperties properties;
    private final AuditActorProvider actorProvider;
    private final AuditEventFactory eventFactory;
    private final DefaultAuditEventPublisher publisher;

    AuditHttpFilter(
            AuditHttpProperties properties,
            AuditActorProvider actorProvider,
            AuditEventFactory eventFactory,
            DefaultAuditEventPublisher publisher) {
        this.properties = properties;
        this.actorProvider = actorProvider;
        this.eventFactory = eventFactory;
        this.publisher = publisher;
    }

    static FilterRegistrationBean<AuditHttpFilter> registration(AuditHttpFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setName("roseAuditHttpFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request.getAttribute(AuditHttpState.ATTRIBUTE) instanceof AuditHttpState) return false;
        return isPreflight(request) || matches(properties.getExclude(), applicationPath(request));
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object existing = request.getAttribute(AuditHttpState.ATTRIBUTE);
        if (existing instanceof AuditHttpState state) {
            dispatchExisting(request, response, chain, state);
            return;
        }

        AuditHttpState state = new AuditHttpState();
        request.setAttribute(AuditHttpState.ATTRIBUTE, state);
        String path = applicationPath(request);
        String includedPattern = matching(properties.getInclude(), path);
        if (includedPattern != null) {
            state.include(request.getMethod() + " " + includedPattern, snapshot(request, null, state));
        }

        try {
            invokeChain(request, response, chain, state);
        } finally {
            finishOrListen(request, response, state);
        }
    }

    private void dispatchExisting(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain, AuditHttpState state)
            throws ServletException, IOException {
        try {
            invokeChain(request, response, chain, state);
        } finally {
            if (request.getDispatcherType() == DispatcherType.ERROR) {
                publishIfEligible(request, response, state);
            } else if (request.isAsyncStarted()) {
                listen(request, response, state);
            }
        }
    }

    private void invokeChain(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain, AuditHttpState state)
            throws ServletException, IOException {
        try {
            chainWithRequestContext(request, response, chain);
        } catch (IOException | ServletException | RuntimeException | Error failure) {
            state.fail(failure);
            throw failure;
        }
    }

    private void chainWithRequestContext(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        RequestAttributes previous = RequestContextHolder.getRequestAttributes();
        if (previous != null) {
            chain.doFilter(request, response);
            return;
        }
        ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            chain.doFilter(request, response);
        } finally {
            completeRequestContext(attributes);
        }
    }

    private static void completeRequestContext(ServletRequestAttributes attributes) {
        RequestContextHolder.resetRequestAttributes();
        try {
            attributes.requestCompleted();
        } catch (RuntimeException lifecycleFailure) {
            LOGGER.warn(
                    "Servlet audit request context cleanup failed (cause={})",
                    lifecycleFailure.getClass().getName());
        }
    }

    private void finishOrListen(HttpServletRequest request, HttpServletResponse response, AuditHttpState state) {
        if (request.isAsyncStarted()) {
            listen(request, response, state);
        } else {
            publishIfEligible(request, response, state);
        }
    }

    private void listen(HttpServletRequest request, HttpServletResponse response, AuditHttpState state) {
        try {
            request.getAsyncContext().addListener(new AsyncListener() {
                @Override
                public void onComplete(AsyncEvent event) {
                    publishIfEligible(request, response, state);
                }

                @Override
                public void onTimeout(AsyncEvent event) {
                    state.fail(new java.util.concurrent.TimeoutException("Servlet request timed out"));
                }

                @Override
                public void onError(AsyncEvent event) {
                    state.fail(event.getThrowable());
                }

                @Override
                public void onStartAsync(AsyncEvent event) throws IOException {
                    event.getAsyncContext().addListener(this);
                }
            });
        } catch (IllegalStateException completed) {
            publishIfEligible(request, response, state);
        }
    }

    private void publishIfEligible(HttpServletRequest request, HttpServletResponse response, AuditHttpState state) {
        AuditHttpState.Decision decision = state.claimCompletion();
        if (decision == null) {
            return;
        }
        Throwable failure = decision.failure();
        Integer status = observedStatus(response.getStatus(), response.isCommitted(), failure);
        long duration = TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - decision.startedNanos()));
        try {
            publisher.publish(eventFactory.createHttp(
                    decision.snapshot(),
                    decision.action(),
                    outcome(status, failure),
                    decision.startedAt(),
                    duration,
                    failure,
                    status));
        } catch (RuntimeException publishFailure) {
            publisher.recordBuildFailure();
            LOGGER.warn(
                    "HTTP audit event could not be created (cause={})",
                    publishFailure.getClass().getName());
        }
    }

    static Integer observedStatus(int status, boolean committed, Throwable failure) {
        if (failure != null && status == 200 && !committed) {
            return null;
        }
        return status;
    }

    static AuditEvent.Outcome outcome(Integer status, Throwable failure) {
        if (failure != null || (status != null && status >= 400)) {
            return AuditEvent.Outcome.FAILURE;
        }
        if (status != null && status >= 200 && status < 400) {
            return AuditEvent.Outcome.SUCCESS;
        }
        return AuditEvent.Outcome.UNKNOWN;
    }

    void captureController(HttpServletRequest request, String action) {
        Object value = request.getAttribute(AuditHttpState.ATTRIBUTE);
        if (value instanceof AuditHttpState state) {
            state.captureController(action, snapshot(request, action, state));
        }
    }

    private AuditEvent snapshot(HttpServletRequest request, String action, AuditHttpState state) {
        String route = action == null ? applicationPath(request) : action.substring(action.indexOf(' ') + 1);
        ServletRequestAttributes attributes = null;
        if (RequestContextHolder.getRequestAttributes() == null) {
            attributes = new ServletRequestAttributes(request);
            RequestContextHolder.setRequestAttributes(attributes);
        }
        try {
            return eventFactory.snapshotHttp(
                    state.requestId(),
                    actorProvider.currentActorId(),
                    actorProvider.currentActorName(),
                    request.getMethod(),
                    route,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
        } catch (RuntimeException failure) {
            publisher.recordBuildFailure();
            LOGGER.warn(
                    "HTTP audit context could not be captured (cause={})",
                    failure.getClass().getName());
            return eventFactory.degradedHttpSnapshot(
                    state.requestId(),
                    request.getMethod(),
                    route,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
        } finally {
            if (attributes != null) completeRequestContext(attributes);
        }
    }

    private static boolean isPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Access-Control-Request-Method") != null;
    }

    private static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }

    private static String matching(List<String> patterns, String path) {
        for (String pattern : patterns) {
            try {
                if (PATH_MATCHER.match(pattern.trim(), path)) return pattern.trim();
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static boolean matches(List<String> patterns, String path) {
        return matching(patterns, path) != null;
    }
}
