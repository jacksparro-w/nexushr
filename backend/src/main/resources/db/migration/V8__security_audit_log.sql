CREATE TABLE security_audit_log (
    id UUID PRIMARY KEY,
    actor_id UUID,
    action VARCHAR(40) NOT NULL,
    resource_path VARCHAR(300) NOT NULL,
    http_status INTEGER NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_security_audit_log_occurred_at ON security_audit_log (occurred_at DESC);
CREATE INDEX idx_security_audit_log_actor ON security_audit_log (actor_id, occurred_at DESC);
