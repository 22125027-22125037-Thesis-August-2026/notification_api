-- ============================================================
-- V3 — Device token registry
--
-- Each row maps an FCM device token to a uMatter profile.
-- A single profile may have multiple rows (phone + tablet + web).
-- The token itself is globally unique (Firebase guarantees this),
-- so it serves as the natural primary key — re-registration of the
-- same token under a different profile is a simple UPDATE.
-- ============================================================

CREATE TABLE device_tokens (
    device_token  TEXT         PRIMARY KEY,
    profile_id    UUID         NOT NULL,
    platform      VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_device_tokens_platform
        CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

-- Supports the consumer's hot path: "find all tokens for this profile".
CREATE INDEX idx_device_tokens_profile_id ON device_tokens (profile_id);
