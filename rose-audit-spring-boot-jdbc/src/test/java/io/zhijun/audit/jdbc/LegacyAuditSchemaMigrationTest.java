package io.zhijun.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies V2 upgrades a populated pre-event-type PostgreSQL schema. */
@Testcontainers
class LegacyAuditSchemaMigrationTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void backfillsUnknownTypeAndAllowsUnknownDurationOnExistingRows() throws Exception {
        try (Connection connection =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE audit_event (
                            event_id VARCHAR(64) PRIMARY KEY,
                            occurred_at TIMESTAMPTZ NOT NULL,
                            duration_ms BIGINT NOT NULL
                        )
                        """);
                statement.execute("""
                        INSERT INTO audit_event (event_id, occurred_at, duration_ms)
                        VALUES ('legacy-event', TIMESTAMPTZ '2026-01-01 00:00:00+00', 23)
                        """);
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/audit-schema-postgresql-v2.sql"));

            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery(
                            "SELECT event_type, duration_ms FROM audit_event WHERE event_id = 'legacy-event'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("event_type")).isEqualTo("UNKNOWN");
                assertThat(rows.getLong("duration_ms")).isEqualTo(23L);
            }
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO audit_event (event_id, occurred_at, duration_ms)
                        VALUES ('unknown-duration', CURRENT_TIMESTAMP, NULL)
                        """);
            }
        }
    }
}
