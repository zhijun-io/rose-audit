CREATE TABLE IF NOT EXISTS audit_event (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    occurred_at TIMESTAMPTZ NOT NULL,
    tenant_id VARCHAR(512),
    request_id VARCHAR(512),
    trace_id VARCHAR(512),
    actor_id VARCHAR(512),
    actor_name VARCHAR(512),
    action VARCHAR(512) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    duration_ms BIGINT,
    error_type VARCHAR(512),
    error_message VARCHAR(2048),
    http_method VARCHAR(16),
    url VARCHAR(2048),
    query_string VARCHAR(2048),
    http_status INTEGER,
    client_ip VARCHAR(512),
    user_agent VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_audit_event_occurred_id ON audit_event (occurred_at DESC, event_id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_action_occurred_id ON audit_event (action, occurred_at DESC, event_id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_actor_occurred_id ON audit_event (actor_id, occurred_at DESC, event_id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_tenant_occurred_id ON audit_event (tenant_id, occurred_at DESC, event_id DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_request_id ON audit_event (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_audit_event_trace_id ON audit_event (trace_id) WHERE trace_id IS NOT NULL;
