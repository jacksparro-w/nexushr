CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    category VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    dedupe_key VARCHAR(255) NOT NULL UNIQUE
);

CREATE INDEX idx_notifications_recipient_created
    ON notifications(recipient_user_id, created_at DESC);

CREATE TABLE announcements (
    id UUID PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    created_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_announcements_created ON announcements(created_at DESC);

CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    recipient_email VARCHAR(320) NOT NULL,
    subject VARCHAR(180) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_email_outbox_due ON email_outbox(status, next_attempt_at);
