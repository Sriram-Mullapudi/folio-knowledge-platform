-- V3__documents.sql
-- Document metadata. The file bytes live in object storage (MinIO/S3); this
-- table is the system of record for everything queryable about a document.

CREATE TABLE documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_filename TEXT NOT NULL,
    content_type      TEXT NOT NULL,
    size_bytes        BIGINT NOT NULL,
    content_hash      TEXT NOT NULL,              -- SHA-256 hex of the bytes
    storage_key       TEXT NOT NULL UNIQUE,       -- object key in the bucket
    status            TEXT NOT NULL DEFAULT 'STORED',
    uploaded_by       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_documents_status CHECK (status IN ('STORED', 'FAILED'))
);

-- Duplicate detection: the same owner cannot upload identical content twice.
-- This unique index is the authoritative guard (it also closes the race window
-- between the application-level check and the insert).
CREATE UNIQUE INDEX uq_documents_owner_hash ON documents (uploaded_by, content_hash);

-- History listing is "my documents, newest first".
CREATE INDEX idx_documents_owner_time ON documents (uploaded_by, created_at DESC);
