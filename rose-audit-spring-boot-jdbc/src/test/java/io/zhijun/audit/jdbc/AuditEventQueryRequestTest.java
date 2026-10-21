package io.zhijun.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verifies query request defaults and validation of supported search criteria. */
class AuditEventQueryRequestTest {
    @Test
    void rejectsEmptyOrReversedTimeRange() {
        Instant from = Instant.parse("2026-01-02T00:00:00Z");

        assertThatThrownBy(() -> new AuditEventQueryRequest(null, null, null, null, null, null, from, from))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before to");
        assertThatThrownBy(
                        () -> new AuditEventQueryRequest(null, null, null, null, null, null, from.plusSeconds(1), from))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("from must be before to");
    }

    @Test
    void allCreatesAnUnfilteredRequest() {
        assertThat(AuditEventQueryRequest.all())
                .isEqualTo(new AuditEventQueryRequest(null, null, null, null, null, null, null, null));
    }
}
