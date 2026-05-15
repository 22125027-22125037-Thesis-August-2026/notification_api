-- ============================================================
-- V1 — In-app notification inbox
-- Stores every alert that this service has handed off (or attempted
-- to hand off) to the user, so the mobile/web client can render a
-- read/unread history.
-- ============================================================

CREATE TABLE in_app_notifications (
    notification_id UUID         PRIMARY KEY,
    profile_id      UUID         NOT NULL,
    title           VARCHAR(255) NOT NULL,
    message         TEXT         NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_in_app_notifications_type
        CHECK (type IN ('BOOKING', 'STREAK', 'CHAT'))
);

CREATE INDEX idx_in_app_notifications_profile_id
    ON in_app_notifications (profile_id);

CREATE INDEX idx_in_app_notifications_created_at
    ON in_app_notifications (created_at DESC);

-- Composite supports the most common query: "give me this user's inbox, newest first".
CREATE INDEX idx_in_app_notifications_profile_created
    ON in_app_notifications (profile_id, created_at DESC);
