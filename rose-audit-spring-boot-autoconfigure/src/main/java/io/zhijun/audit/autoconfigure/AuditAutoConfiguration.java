package io.zhijun.audit.autoconfigure;

import io.zhijun.audit.AuditActorProvider;
import io.zhijun.audit.AuditCorrelationContext;
import io.zhijun.audit.AuditCorrelationContextProvider;
import io.zhijun.audit.AuditEventConsumer;
import io.zhijun.audit.AuditRequestContextProvider;
import io.zhijun.audit.AuditTenantProvider;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configures asynchronous audit consumption when an {@link AuditEventConsumer} bean is available. Successful events
 * are queued after business commit; failed events are queued after transaction completion. Events published outside a
 * transaction are queued immediately. The default executor has two core threads, up to four threads, a queue capacity
 * of 200, and waits up to 30 seconds for queued tasks during shutdown. Its in-memory queue is not durable: rejected or
 * not-yet-consumed events can be lost. Applications can replace the consumer bean to use another destination and can
 * replace the executor with a bean named {@code auditEventTaskExecutor}.
 *
 * <p>A replacement executor should schedule tasks asynchronously; an inline executor makes consumption run on the
 * transaction-completion thread. The JDBC consumer starts its own transaction when it writes an event.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
public class AuditAutoConfiguration {
    @Bean
    DefaultAuditEventPublisher auditEventPublisher(
            ApplicationEventPublisher eventPublisher, ObjectProvider<AuditEventListener.Metrics> metricsProvider) {
        return new DefaultAuditEventPublisher(
                eventPublisher, metricsProvider.getIfAvailable(() -> AuditEventListener.Metrics.NOOP));
    }

    @Bean
    @ConditionalOnMissingBean
    AuditActorProvider auditActorProvider() {
        return () -> "system";
    }

    @Bean
    @ConditionalOnMissingBean
    AuditTenantProvider auditTenantProvider() {
        return () -> null;
    }

    @Bean
    @ConditionalOnMissingBean
    AuditCorrelationContextProvider auditCorrelationContextProvider() {
        return AuditCorrelationContext::empty;
    }

    @Bean
    AuditEventFactory auditEventFactory(
            AuditTenantProvider tenantProvider,
            AuditCorrelationContextProvider correlationContextProvider,
            ObjectProvider<AuditRequestContextProvider> requestContextProvider) {
        return new AuditEventFactory(tenantProvider, correlationContextProvider, requestContextProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    AuditEntryPointAdvisor auditEntryPointAdvisor(
            DefaultAuditEventPublisher publisher, AuditActorProvider actorProvider, AuditEventFactory eventFactory) {
        return new AuditEntryPointAdvisor(publisher, actorProvider, eventFactory);
    }

    @Bean(name = "auditEventTaskExecutor")
    @ConditionalOnBean(AuditEventConsumer.class)
    @ConditionalOnMissingBean(name = "auditEventTaskExecutor")
    ThreadPoolTaskExecutor auditEventTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("audit-event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    @ConditionalOnBean(AuditEventConsumer.class)
    @ConditionalOnMissingBean(AuditEventListener.class)
    AuditEventListener auditEventListener(
            AuditEventConsumer consumer,
            @Qualifier("auditEventTaskExecutor") TaskExecutor auditEventTaskExecutor,
            ObjectProvider<AuditEventListener.Metrics> metricsProvider) {
        return new AuditEventListener(
                consumer,
                auditEventTaskExecutor,
                metricsProvider.getIfAvailable(() -> AuditEventListener.Metrics.NOOP));
    }
}
