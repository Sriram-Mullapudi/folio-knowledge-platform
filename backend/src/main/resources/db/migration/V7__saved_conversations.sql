CREATE TABLE saved_conversations (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 title VARCHAR(120) NOT NULL,
 snapshot JSONB NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_owner_updated ON saved_conversations(owner_id, updated_at DESC);
