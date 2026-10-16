-- V4__document_processing.sql
-- Adds the processing lifecycle to documents and the chunks produced by it.

-- 1. Evolve the document status lifecycle.
--    Phase 3 used STORED/FAILED; processing introduces a real pipeline:
--    UPLOADED -> PROCESSING -> COMPLETED | FAILED.
ALTER TABLE documents DROP CONSTRAINT IF EXISTS chk_documents_status;
UPDATE documents SET status = 'UPLOADED' WHERE status = 'STORED';
ALTER TABLE documents ALTER COLUMN status SET DEFAULT 'UPLOADED';
ALTER TABLE documents
    ADD CONSTRAINT chk_documents_status
    CHECK (status IN ('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED'));

-- 2. Processing result columns on the document.
ALTER TABLE documents ADD COLUMN chunk_count      INTEGER;
ALTER TABLE documents ADD COLUMN processed_at     TIMESTAMPTZ;
ALTER TABLE documents ADD COLUMN processing_error TEXT;

-- 3. Chunks: the unit of retrieval. Embeddings (a pgvector column) are added
--    in a later phase; for now we persist the text and its position/metadata.
CREATE TABLE document_chunks (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id    UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index    INTEGER NOT NULL,
    content        TEXT NOT NULL,
    char_count     INTEGER NOT NULL,
    token_estimate INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per (document, position); also the natural ordering for retrieval.
CREATE UNIQUE INDEX uq_chunks_document_index ON document_chunks (document_id, chunk_index);
