package io.zhijun.audit.jdbc;

import io.zhijun.audit.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Application-facing operations for creating and searching persisted audit events. The JDBC module provides a
 * default implementation; applications can replace the service to customize persistence and query behavior.
 */
public interface AuditEventService {
    /** Stores one audit event. The default JDBC implementation uses an independent transaction for this write. */
    void create(AuditEvent event);

    /** Returns a page of events matching the optional criteria and Spring Data page and sort request. */
    Page<AuditEventRecord> search(AuditEventQueryRequest filter, Pageable pageable);
}
