ALTER TABLE document_chunks ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;
CREATE INDEX idx_chunks_search ON document_chunks USING GIN (search_vector);
