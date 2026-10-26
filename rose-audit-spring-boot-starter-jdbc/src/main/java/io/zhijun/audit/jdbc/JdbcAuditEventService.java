package io.zhijun.audit.jdbc;

import io.zhijun.audit.AuditEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit events and provides paginated JDBC queries. Each create call runs in a new transaction so the audit
 * write is committed independently of the business transaction that produced the event. Searches use bound
 * parameters for filter values, allow only known sort columns, cap page size, and return Spring Data {@link Page}
 * results. The table must be created using the PostgreSQL schema script provided by this module.
 */
public class JdbcAuditEventService implements AuditEventService {
    private static final String INSERT_SQL = """
            INSERT INTO audit_event (
                event_id, event_type, occurred_at, tenant_id, request_id, trace_id, actor_id, actor_name, action,
                outcome, duration_ms, error_type, error_message, http_method, url, query_string, http_status,
                client_ip, user_agent
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_COLUMNS = """
            SELECT event_id, event_type, occurred_at, tenant_id, request_id, trace_id, actor_id, actor_name, action,
                   outcome, duration_ms, error_type, error_message, http_method, url, query_string, http_status,
                   client_ip, user_agent
            FROM audit_event
            """;
    private static final int MAX_PAGE_SIZE = 200;
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "occurredAt", "occurred_at",
            "eventId", "event_id",
            "tenantId", "tenant_id",
            "requestId", "request_id",
            "traceId", "trace_id",
            "actorId", "actor_id",
            "actorName", "actor_name",
            "action", "action",
            "outcome", "outcome");

    private final JdbcTemplate jdbcTemplate;

    JdbcAuditEventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(AuditEvent event) {
        jdbcTemplate.update(
                INSERT_SQL,
                event.eventId(),
                event.type().name(),
                Timestamp.from(event.occurredAt()),
                event.tenantId(),
                event.requestId(),
                event.traceId(),
                event.actorId(),
                event.actorName(),
                event.action(),
                event.outcome().name(),
                event.durationMs(),
                event.errorType(),
                event.errorMessage(),
                event.httpMethod(),
                event.url(),
                event.queryString(),
                event.httpStatus(),
                event.clientIp(),
                event.userAgent());
    }

    @Override
    public Page<AuditEventRecord> search(AuditEventQueryRequest filter, Pageable pageable) {
        if (pageable == null || !pageable.isPaged()) {
            throw new IllegalArgumentException("pageable must be paged");
        }
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String orderBy = orderBy(pageable.getSort());
        SqlFilter sqlFilter = filter(filter == null ? AuditEventQueryRequest.all() : filter);
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event" + sqlFilter.clause(),
                Long.class,
                sqlFilter.parameters().toArray());
        var parameters = new ArrayList<>(sqlFilter.parameters());
        parameters.add(pageable.getPageSize());
        parameters.add(pageable.getOffset());
        List<AuditEventRecord> records = jdbcTemplate.query(
                SELECT_COLUMNS + sqlFilter.clause() + orderBy + " LIMIT ? OFFSET ?",
                this::mapRow,
                parameters.toArray());
        return new PageImpl<>(records, pageable, total);
    }

    private String orderBy(Sort sort) {
        if (sort.isUnsorted()) {
            return " ORDER BY occurred_at DESC, event_id DESC";
        }
        var columns = new ArrayList<String>();
        boolean sortsByEventId = false;
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                throw new IllegalArgumentException("unsupported sort property: " + order.getProperty());
            }
            columns.add(column + (order.isAscending() ? " ASC" : " DESC"));
            sortsByEventId |= column.equals("event_id");
        }
        if (!sortsByEventId) {
            columns.add("event_id DESC");
        }
        return " ORDER BY " + String.join(", ", columns);
    }

    private SqlFilter filter(AuditEventQueryRequest filter) {
        var clause = new StringBuilder(" WHERE 1 = 1");
        var parameters = new ArrayList<Object>();
        addFilter(clause, parameters, "tenant_id", filter.tenantId());
        addFilter(clause, parameters, "request_id", filter.requestId());
        addFilter(clause, parameters, "trace_id", filter.traceId());
        addFilter(clause, parameters, "actor_id", filter.actorId());
        addFilter(clause, parameters, "action", filter.action());
        if (filter.outcome() != null) {
            clause.append(" AND outcome = ?");
            parameters.add(filter.outcome().name());
        }
        if (filter.from() != null) {
            clause.append(" AND occurred_at >= ?");
            parameters.add(Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            clause.append(" AND occurred_at < ?");
            parameters.add(Timestamp.from(filter.to()));
        }
        return new SqlFilter(clause.toString(), parameters);
    }

    private AuditEventRecord mapRow(ResultSet row, int rowNumber) throws SQLException {
        return new AuditEventRecord(
                row.getString("event_id"),
                AuditEvent.Type.valueOf(row.getString("event_type")),
                row.getTimestamp("occurred_at").toInstant(),
                row.getString("tenant_id"),
                row.getString("request_id"),
                row.getString("trace_id"),
                row.getString("actor_id"),
                row.getString("actor_name"),
                row.getString("action"),
                AuditEvent.Outcome.valueOf(row.getString("outcome")),
                row.getObject("duration_ms", Long.class),
                row.getString("error_type"),
                row.getString("error_message"),
                row.getString("http_method"),
                row.getString("url"),
                row.getString("query_string"),
                (Integer) row.getObject("http_status"),
                row.getString("client_ip"),
                row.getString("user_agent"));
    }

    private void addFilter(StringBuilder where, List<Object> parameters, String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            parameters.add(value);
        }
    }

    /** Holds a generated WHERE fragment and its bound values for a paged event query. */
    private record SqlFilter(String clause, List<Object> parameters) {}
}
