package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditRequestContextProvider;
import io.zhijun.audit.web.ServletAuditRequestContextProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** Verifies merged Controller and scheduled-entry auditing through one advisor. */
class AuditEntryPointAdvisorTest {
    private final List<AuditEvent> events = new ArrayList<>();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void publishesOneEventWhenMethodIsBothControllerHandlerAndScheduledEntry() throws Exception {
        var target = new TestController();
        Method handlerMethod = TestController.class.getMethod("run");
        var request = new MockHttpServletRequest("GET", "/run");
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new HandlerMethod(target, handlerMethod));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));

        var proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvisor(advisor());
        ((TestController) proxyFactory.getProxy()).run();

        assertThat(events)
                .singleElement()
                .extracting(AuditEvent::action, AuditEvent::outcome)
                .containsExactly("TestController.run", AuditEvent.Outcome.SUCCESS);
    }

    @Test
    void recognizesScheduledMetaAnnotationOnNonControllerBean() {
        var target = new ScheduledOperations();
        var proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvisor(advisor());

        ((ScheduledOperations) proxyFactory.getProxy()).run();

        assertThat(events)
                .singleElement()
                .extracting(AuditEvent::action, AuditEvent::outcome)
                .containsExactly("ScheduledOperations.run", AuditEvent.Outcome.SUCCESS);
    }

    @Test
    void recognizesComposedRestControllerAnnotation() throws Exception {
        var target = new ComposedController();
        Method handlerMethod = ComposedController.class.getMethod("handle");
        setHandler(target, handlerMethod);
        var proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvisor(advisor());

        ((ComposedController) proxyFactory.getProxy()).handle();

        assertThat(events)
                .singleElement()
                .extracting(AuditEvent::action, AuditEvent::outcome)
                .containsExactly("ComposedController.handle", AuditEvent.Outcome.SUCCESS);
    }

    @Test
    void recordsFailureAndRethrowsTheOriginalException() throws Exception {
        var target = new FailingController();
        Method handlerMethod = FailingController.class.getMethod("fail");
        setHandler(target, handlerMethod);
        var proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvisor(advisor());

        assertThatThrownBy(() -> ((FailingController) proxyFactory.getProxy()).fail())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operation failed");

        assertThat(events)
                .singleElement()
                .extracting(AuditEvent::action, AuditEvent::outcome, AuditEvent::errorType)
                .containsExactly(
                        "FailingController.fail", AuditEvent.Outcome.FAILURE, IllegalStateException.class.getName());
    }

    @Test
    void recordsAsyncControllerAsSuccessfulWhenMethodReturnsBeforeFutureCompletes() throws Exception {
        var target = new AsyncController();
        Method handlerMethod = AsyncController.class.getMethod("handle");
        setHandler(target, handlerMethod);
        var proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvisor(advisor());

        CompletableFuture<String> result = ((AsyncController) proxyFactory.getProxy()).handle();

        assertThat(result).isNotDone();
        assertThat(events)
                .singleElement()
                .extracting(AuditEvent::action, AuditEvent::outcome)
                .containsExactly("AsyncController.handle", AuditEvent.Outcome.SUCCESS);
    }

    private void setHandler(Object target, Method method) {
        var request = new MockHttpServletRequest("GET", "/run");
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new HandlerMethod(target, method));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }

    private AuditEntryPointAdvisor advisor() {
        @SuppressWarnings("unchecked")
        ObjectProvider<AuditRequestContextProvider> requestContextProvider = mock(ObjectProvider.class);
        when(requestContextProvider.getIfAvailable()).thenReturn(new ServletAuditRequestContextProvider());
        var eventFactory = new AuditEventFactory(() -> null, AuditCorrelationContext::empty, requestContextProvider);
        return new AuditEntryPointAdvisor(
                new DefaultAuditEventPublisher(event -> events.add((AuditEvent) event)),
                () -> "test-user",
                eventFactory);
    }

    @RestController
    static class TestController {
        @Scheduled(fixedDelay = Long.MAX_VALUE)
        public void run() {}
    }

    @Scheduled(fixedDelay = Long.MAX_VALUE)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ScheduledEntry {}

    @RestController
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface ComposedControllerAnnotation {}

    @ComposedControllerAnnotation
    static class ComposedController {
        public void handle() {}
    }

    static class ScheduledOperations {
        @ScheduledEntry
        public void run() {}
    }

    @RestController
    static class FailingController {
        public void fail() {
            throw new IllegalStateException("operation failed");
        }
    }

    @RestController
    static class AsyncController {
        public CompletableFuture<String> handle() {
            return new CompletableFuture<>();
        }
    }
}
