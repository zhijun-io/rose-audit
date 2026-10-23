package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditActorProvider;
import io.zhijun.audit.AuditEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.annotation.MergedAnnotations;

/**
 * Audits public methods annotated with supported scheduler or message-listener annotations. Framework annotations are
 * matched by name, so Kafka, RabbitMQ, and JMS integrations remain optional dependencies.
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
    private static final Set<String> KAFKA_HANDLER_ANNOTATIONS =
            Set.of("org.springframework.kafka.annotation.KafkaHandler");
    private static final Set<String> SCHEDULED_ANNOTATIONS = Set.of(
            "org.springframework.scheduling.annotation.Scheduled",
            "org.springframework.scheduling.annotation.Schedules");
    private static final Set<String> MESSAGE_ANNOTATIONS = Set.of(
            "org.springframework.kafka.annotation.KafkaListener", "org.springframework.kafka.annotation.KafkaListeners",
            "org.springframework.amqp.rabbit.annotation.RabbitListener",
                    "org.springframework.amqp.rabbit.annotation.RabbitListeners",
            "org.springframework.jms.annotation.JmsListener", "org.springframework.jms.annotation.JmsListeners");
    private static final Set<String> CLASS_LEVEL_KAFKA_LISTENER_ANNOTATIONS = Set.of(
            "org.springframework.kafka.annotation.KafkaListener",
            "org.springframework.kafka.annotation.KafkaListeners");

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
        if (!Modifier.isPublic(method.getModifiers())) {
            return false;
        }
        return isEntryPoint(method, targetClass);
    }

    private Object record(MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = AopUtils.getTargetClass(invocation.getThis());
        Method method = AopUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
        if (!isEntryPoint(method, targetClass)) {
            return invocation.proceed();
        }

        String action = targetClass.getSimpleName() + "." + method.getName();
        long startedNanos = System.nanoTime();
        Instant startedAt = Instant.now();
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable businessFailure) {
            publish(
                    entryType(method, targetClass),
                    action,
                    AuditEvent.Outcome.FAILURE,
                    startedAt,
                    TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedNanos)),
                    businessFailure);
            throw businessFailure;
        }
        if (isAsyncResult(result, method)) {
            publish(entryType(method, targetClass), action, AuditEvent.Outcome.UNKNOWN, startedAt, null, null);
        } else {
            publish(
                    entryType(method, targetClass),
                    action,
                    AuditEvent.Outcome.SUCCESS,
                    startedAt,
                    TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedNanos)),
                    null);
        }
        return result;
    }

    private void publish(
            AuditEvent.Type type,
            String action,
            AuditEvent.Outcome outcome,
            Instant startedAt,
            Long durationMs,
            Throwable failure) {
        try {
            publisher.publish(eventFactory.create(
                    type,
                    actorProvider.currentActorId(),
                    actorProvider.currentActorName(),
                    action,
                    outcome,
                    startedAt,
                    durationMs,
                    failure));
        } catch (RuntimeException publishFailure) {
            LOGGER.warn(
                    "Audit event publishing failed (type={}, outcome={}, cause={})",
                    type,
                    outcome,
                    publishFailure.getClass().getName());
        }
    }

    private boolean hasAnnotation(java.lang.reflect.AnnotatedElement element, Set<String> annotationNames) {
        return MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).stream()
                .anyMatch(annotation ->
                        annotationNames.contains(annotation.getType().getName()));
    }

    private boolean isEntryPoint(Method method, Class<?> targetClass) {
        return hasAnnotation(method, ENTRY_POINT_ANNOTATIONS)
                || (hasAnnotation(method, KAFKA_HANDLER_ANNOTATIONS)
                        && hasAnnotation(targetClass, CLASS_LEVEL_KAFKA_LISTENER_ANNOTATIONS));
    }

    private AuditEvent.Type entryType(Method method, Class<?> targetClass) {
        if (hasAnnotation(method, SCHEDULED_ANNOTATIONS)) return AuditEvent.Type.SCHEDULED_TASK;
        if (hasAnnotation(method, MESSAGE_ANNOTATIONS)
                || hasAnnotation(method, KAFKA_HANDLER_ANNOTATIONS)
                || hasAnnotation(targetClass, CLASS_LEVEL_KAFKA_LISTENER_ANNOTATIONS)) return AuditEvent.Type.MESSAGE;
        return AuditEvent.Type.OPERATION;
    }

    private boolean isAsyncResult(Object result, Method method) {
        if (result instanceof Future<?> || result instanceof CompletionStage<?>) return true;
        return hasAsyncType(method.getReturnType());
    }

    private boolean hasAsyncType(Class<?> type) {
        if (type.getName().equals("org.reactivestreams.Publisher")) return true;
        for (Class<?> implemented : type.getInterfaces()) if (hasAsyncType(implemented)) return true;
        Class<?> parent = type.getSuperclass();
        return parent != null && parent != Object.class && hasAsyncType(parent);
    }
}
