package io.zhijun.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.zhijun.audit.AuditActorProvider;
import io.zhijun.audit.AuditEvent;
import io.zhijun.audit.AuditTenantProvider;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
        classes = JdbcAuditIntegrationTest.TestApplication.class,
        properties = {
            "spring.sql.init.mode=always",
            "spring.sql.init.schema-locations=classpath:db/audit-schema-postgresql.sql,classpath:business-schema.sql"
        })
@Testcontainers
/** Exercises the full transaction-to-consumer-to-JDBC flow against the test database. */
class JdbcAuditIntegrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private AuditedOperations operations;

    @Autowired
    private RollbackCaller rollbackCaller;

    @Autowired
    private TestAuditEventConsumer testAuditEventConsumer;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM operation_record");
        testAuditEventConsumer.clear();
    }

    @Test
    void persistsSuccessfulEventsAndSupportsSpringDataPaging() {
        operations.succeed("first");
        operations.succeed("second");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_event WHERE action = 'AuditedOperations.succeed'",
                                Integer.class))
                        .isEqualTo(2));

        var filter = filterFor("AuditedOperations.succeed");
        var firstPage = auditEventService.search(filter, PageRequest.of(0, 1));
        var secondPage = auditEventService.search(filter, PageRequest.of(1, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent().getFirst().outcome()).isEqualTo(AuditEvent.Outcome.SUCCESS);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getContent().getFirst().action()).isEqualTo("AuditedOperations.succeed");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_record", Integer.class))
                .isEqualTo(2);
        assertThat(testAuditEventConsumer.events).hasSize(2);
    }

    @Test
    void persistsFailureEventAfterBusinessTransactionRollsBack() {
        assertThatThrownBy(() -> operations.fail("rolled-back"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("operation failed");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM audit_event WHERE action = 'AuditedOperations.fail'",
                                Integer.class))
                        .isEqualTo(1));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_record", Integer.class))
                .isZero();
        assertThat(testAuditEventConsumer.events)
                .singleElement()
                .extracting(AuditEvent::outcome)
                .isEqualTo(AuditEvent.Outcome.FAILURE);
    }

    @Test
    void doesNotPersistSuccessEventWhenEnclosingBusinessTransactionRollsBack() {
        assertThatThrownBy(() -> rollbackCaller.succeedThenRollback("rolled-back-success"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("caller failed");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_record", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class))
                .isZero();
    }

    @Test
    void searchesByTenantOutcomeAndHalfOpenTimeRange() {
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = Instant.parse("2026-01-02T00:00:00Z");
        auditEventService.create(storedEvent("tenant-a", "alpha", AuditEvent.Outcome.SUCCESS, first));
        auditEventService.create(storedEvent("tenant-b", "beta", AuditEvent.Outcome.FAILURE, second));

        var matches = auditEventService.search(
                new AuditEventQueryRequest(
                        "tenant-a", null, null, null, null, AuditEvent.Outcome.SUCCESS, first, second),
                PageRequest.of(0, 20, Sort.by("occurredAt")));
        var everything = auditEventService.search(AuditEventQueryRequest.all(), PageRequest.of(0, 20));

        assertThat(matches.getTotalElements()).isEqualTo(1);
        assertThat(matches.getContent())
                .singleElement()
                .extracting(AuditEventRecord::action)
                .isEqualTo("alpha");
        assertThat(everything.getTotalElements()).isEqualTo(2);
    }

    private AuditEvent storedEvent(String tenantId, String action, AuditEvent.Outcome outcome, Instant occurredAt) {
        return new AuditEvent(
                occurredAt,
                tenantId,
                null,
                null,
                "actor",
                null,
                action,
                outcome,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private AuditEventQueryRequest filterFor(String action) {
        return new AuditEventQueryRequest("tenant-test", null, null, "test-user", action, null, null, null);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    /** Minimal Spring context used to test JDBC audit auto-configuration. */
    static class TestApplication {
        @Bean
        AuditActorProvider auditActorProvider() {
            return () -> "test-user";
        }

        @Bean
        AuditTenantProvider auditTenantProvider() {
            return () -> "tenant-test";
        }

        @Bean
        AuditedOperations auditedOperations(JdbcTemplate jdbcTemplate) {
            return new AuditedOperations(jdbcTemplate);
        }

        @Bean
        TestAuditEventConsumer testAuditEventConsumer() {
            return new TestAuditEventConsumer();
        }

        @Bean
        RollbackCaller rollbackCaller(AuditedOperations operations) {
            return new RollbackCaller(operations);
        }
    }

    /** Captures asynchronously consumed events for integration assertions. */
    static class TestAuditEventConsumer {
        private final java.util.List<AuditEvent> events = new java.util.ArrayList<>();

        @EventListener
        void consume(AuditEvent event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }
    }

    /** Test business methods whose audit events follow their transaction outcome. */
    static class AuditedOperations {
        private final JdbcTemplate jdbcTemplate;

        AuditedOperations(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        @Scheduled(fixedDelay = Long.MAX_VALUE)
        public void succeed(String id) {
            jdbcTemplate.update("INSERT INTO operation_record (id) VALUES (?)", id);
        }

        @Transactional
        @Scheduled(fixedDelay = Long.MAX_VALUE)
        public void fail(String id) {
            jdbcTemplate.update("INSERT INTO operation_record (id) VALUES (?)", id);
            throw new IllegalStateException("operation failed");
        }
    }

    /** Invokes a successful audited operation and then rolls its enclosing transaction back. */
    static class RollbackCaller {
        private final AuditedOperations operations;

        RollbackCaller(AuditedOperations operations) {
            this.operations = operations;
        }

        @Transactional
        public void succeedThenRollback(String id) {
            operations.succeed(id);
            throw new IllegalStateException("caller failed");
        }
    }
}
