-- V2__auth.sql
-- Authentication, refresh-token rotation and audit logging.

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,                  -- BCrypt; never plaintext
    role          TEXT NOT NULL DEFAULT 'USER',   -- ADMIN | USER
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'USER'))
);

-- Refresh tokens are stored HASHED. A stolen DB row therefore cannot be
-- replayed as a bearer token. Rotation revokes the old row on each refresh.
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);

-- Append-only audit trail. No UPDATE/DELETE happens in application code.
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id      UUID REFERENCES users(id),
    action        TEXT NOT NULL,        -- LOGIN | LOGIN_FAILED | REGISTER | LOGOUT | ...
    resource_type TEXT,
    resource_id   UUID,
    metadata      JSONB NOT NULL DEFAULT '{}',
    ip_address    TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_actor_time ON audit_log (actor_id, created_at DESC);
CREATE INDEX idx_audit_action_time ON audit_log (action, created_at DESC);
