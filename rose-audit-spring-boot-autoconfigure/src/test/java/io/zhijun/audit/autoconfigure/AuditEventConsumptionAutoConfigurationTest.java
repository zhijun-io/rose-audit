package io.zhijun.audit.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.zhijun.audit.AuditEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Verifies default consumption infrastructure activation and application bean overrides. */
class AuditEventConsumptionAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AuditAutoConfiguration.class));

    @Test
    void createsBoundedAsyncConsumerInfrastructureWithoutJdbc() {
        contextRunner
                .withBean(AuditEventConsumer.class, () -> mock(AuditEventConsumer.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventListener.class);
                    ThreadPoolTaskExecutor executor =
                            context.getBean("auditEventTaskExecutor", ThreadPoolTaskExecutor.class);
                    assertThat(executor.getCorePoolSize()).isEqualTo(2);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(4);
                    assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity())
                            .isEqualTo(200);
                });
    }

    @Test
    void backsOffWhenApplicationProvidesTheExecutor() {
        TaskExecutor customExecutor = Runnable::run;
        contextRunner
                .withBean(AuditEventConsumer.class, () -> mock(AuditEventConsumer.class))
                .withBean("auditEventTaskExecutor", TaskExecutor.class, () -> customExecutor)
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditEventListener.class);
                    assertThat(context.getBean("auditEventTaskExecutor")).isSameAs(customExecutor);
                    assertThat(context).doesNotHaveBean(ThreadPoolTaskExecutor.class);
                });
    }

    @Test
    void doesNotCreateAnExecutorWithoutAnyConsumer() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AuditEventListener.class);
            assertThat(context).doesNotHaveBean("auditEventTaskExecutor");
        });
    }
}
