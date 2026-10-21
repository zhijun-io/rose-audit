package io.zhijun.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies JDBC audit event insertion and pageable query filtering. */
class JdbcAuditEventServiceTest {
    private final JdbcAuditEventService service = new JdbcAuditEventService(mock(JdbcTemplate.class));

    @Test
    void rejectsUnpagedSearches() {
        assertThatThrownBy(() -> service.search(null, Pageable.unpaged()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageable must be paged");
    }

    @Test
    void rejectsPageSizesAboveTheMaximum() {
        assertThatThrownBy(() -> service.search(null, PageRequest.of(0, 201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page size must be between 1 and 200");
    }

    @Test
    void rejectsUnsupportedSortPropertiesBeforeQuerying() {
        assertThatThrownBy(() -> service.search(null, PageRequest.of(0, 10, Sort.by("unknown"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unsupported sort property: unknown");
    }
}
