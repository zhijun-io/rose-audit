package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditActorProvider;
import io.zhijun.audit.AuditEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Audits active MVC request mappings and public methods annotated with supported scheduler or message-listener
 * annotations. Framework annotations are matched by name, so Kafka, RabbitMQ, and JMS integrations remain optional
 * dependencies. Error controllers are skipped so a container error dispatch is not recorded as a successful call.
 * Apply listener annotations only to entry methods; the services they call should not be audited automatically.
 */
final class AuditEntryPointAdvisor extends StaticMethodMatcherPointcutAdvisor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEntryPointAdvisor.class);
    private static final Set<String> ENTRY_POINT_ANNOTATIONS = Set.of(
            "org.springframework.scheduling.annotation.Scheduled",
            "org.springframework.scheduling.annotation.Schedules",
            "org.springframework.kafka.annotation.KafkaListener",
            "org.springframework.kafka.annotation.KafkaListeners",
            "org.springframework.amqp.rabbit.annotation.RabbitListener",
            "org.springframework.amqp.rabbit.annotation.RabbitListeners",
            "org.springframework.jms.annotation.JmsListener",
            "org.springframework.jms.annotation.JmsListeners");
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
            "org.springframework.stereotype.Controller", "org.springframework.web.bind.annotation.RestController");
    private static final Set<String> REQUEST_MAPPING_ANNOTATIONS =
            Set.of("org.springframework.web.bind.annotation.RequestMapping");
    private static final String ERROR_CONTROLLER = "org.springframework.boot.webmvc.error.ErrorController";

    private final DefaultAuditEventPublisher publisher;
    private final AuditActorProvider actorProvider;
    private final AuditEventFactory eventFactory;

    AuditEntryPointAdvisor(
            DefaultAuditEventPublisher publisher, AuditActorProvider actorProvider, AuditEventFactory eventFactory) {
        this.publisher = publisher;
        this.actorProvider = actorProvider;
        this.eventFactory = eventFactory;
        setAdvice((MethodInterceptor) this::record);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        if (!Modifier.isPublic(method.getModifiers()) || isErrorController(targetClass)) {
            return false;
        }
        return hasAnnotation(method, ENTRY_POINT_ANNOTATIONS)
                || (hasAnnotation(targetClass, CONTROLLER_ANNOTATIONS)
                        && hasAnnotation(method, REQUEST_MAPPING_ANNOTATIONS));
    }

    private Object record(MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = AopUtils.getTargetClass(invocation.getThis());
        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
        if (!hasAnnotation(method, ENTRY_POINT_ANNOTATIONS) && !isActiveControllerHandler(targetClass, method)) {
            return invocation.proceed();
        }

        String action = targetClass.getSimpleName() + "." + method.getName();
        long startedNanos = System.nanoTime();
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable businessFailure) {
            publish(action, AuditEvent.Outcome.FAILURE, startedNanos, businessFailure);
            throw businessFailure;
        }
        publish(action, AuditEvent.Outcome.SUCCESS, startedNanos, null);
        return result;
    }

    private void publish(String action, AuditEvent.Outcome outcome, long startedNanos, Throwable failure) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedNanos));
        try {
            publisher.publish(eventFactory.create(
                    actorProvider.currentActorId(),
                    actorProvider.currentActorName(),
                    action,
                    outcome,
                    durationMs,
                    failure));
        } catch (RuntimeException publishFailure) {
            LOGGER.warn("Audit event publishing failed (action={}, outcome={})", action, outcome, publishFailure);
        }
    }

    private boolean isErrorController(Class<?> targetClass) {
        if (ERROR_CONTROLLER.equals(targetClass.getName())) {
            return true;
        }
        for (Class<?> type : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            if (ERROR_CONTROLLER.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(java.lang.reflect.AnnotatedElement element, Set<String> annotationNames) {
        return MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).stream()
                .anyMatch(annotation ->
                        annotationNames.contains(annotation.getType().getName()));
    }

    private boolean isActiveControllerHandler(Class<?> targetClass, Method method) {
        if (!hasAnnotation(targetClass, CONTROLLER_ANNOTATIONS)) {
            return false;
        }
        ClassLoader classLoader = targetClass.getClassLoader();
        if (!ClassUtils.isPresent("org.springframework.web.servlet.HandlerMapping", classLoader)
                || !ClassUtils.isPresent("jakarta.servlet.http.HttpServletRequest", classLoader)) {
            return false;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        Object handler = attributes.getRequest().getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        Method mappedMethod = AopUtils.getMostSpecificMethod(handlerMethod.getMethod(), handlerMethod.getBeanType());
        return mappedMethod.equals(method);
    }
}
