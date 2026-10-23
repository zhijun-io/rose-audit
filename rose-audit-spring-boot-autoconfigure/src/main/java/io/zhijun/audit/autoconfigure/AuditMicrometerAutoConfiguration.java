package io.zhijun.audit.autoconfigure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.zhijun.audit.AuditEvent;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds audit metrics only when Micrometer and an application {@link MeterRegistry} are available. */
@AutoConfiguration(
        after = AuditAutoConfiguration.class,
        afterName = {
            "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
            "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration"
        })
@ConditionalOnProperty(prefix = "rose.audit", name = "enabled", matchIfMissing = true)
public class AuditMicrometerAutoConfiguration {
    /** Isolates Micrometer references so applications without Micrometer can still load the core auto-configuration. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MicrometerConfiguration {
        private static final Logger LOGGER = LoggerFactory.getLogger(AuditMicrometerAutoConfiguration.class);

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        AuditEventListener.Metrics auditEventMetrics(MeterRegistry registry) {
            return new AuditEventListener.Metrics() {
                @Override
                public void queued(AuditEvent.Outcome outcome) {
                    increment(registry, "rose.audit.events.queued", outcome);
                }

                @Override
                public void consumed(AuditEvent.Outcome outcome) {
                    increment(registry, "rose.audit.events.consumed", outcome);
                }

                @Override
                public void failed(String stage, AuditEvent.Outcome outcome) {
                    recordSafely(() -> Counter.builder("rose.audit.events.failures")
                            .description("Audit event delivery failures by stage")
                            .tag("stage", stage)
                            .tag("outcome", tag(outcome))
                            .register(registry)
                            .increment());
                }

                @Override
                public void consumerDuration(AuditEvent.Outcome outcome, long durationNanos) {
                    recordSafely(() -> Timer.builder("rose.audit.consumer.duration")
                            .description("Time spent consuming an audit event")
                            .tag("outcome", tag(outcome))
                            .register(registry)
                            .record(durationNanos, TimeUnit.NANOSECONDS));
                }
            };
        }

        private static void recordSafely(Runnable recording) {
            try {
                recording.run();
            } catch (RuntimeException metricsFailure) {
                LOGGER.warn(
                        "Could not record audit metrics (cause={})",
                        metricsFailure.getClass().getName());
            }
        }

        private static Counter counter(MeterRegistry registry, String name, AuditEvent.Outcome outcome) {
            return Counter.builder(name)
                    .description("Audit event delivery count")
                    .tag("outcome", tag(outcome))
                    .register(registry);
        }

        private static void increment(MeterRegistry registry, String name, AuditEvent.Outcome outcome) {
            recordSafely(() -> counter(registry, name, outcome).increment());
        }

        private static String tag(AuditEvent.Outcome outcome) {
            return outcome.name().toLowerCase(Locale.ROOT);
        }
    }
}
