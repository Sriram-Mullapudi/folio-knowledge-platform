-- V5__embeddings.sql
-- Adds vector embeddings to chunks and an approximate-nearest-neighbour index.

-- Embedding storage. The dimension is fixed to the model's output (1536 for
-- OpenAI text-embedding-3-small) and MUST match ekp.embedding.dimensions.
-- Changing models to a different dimension requires a new migration.
ALTER TABLE document_chunks ADD COLUMN embedding       vector(1536);
ALTER TABLE document_chunks ADD COLUMN embedding_model TEXT;
ALTER TABLE document_chunks ADD COLUMN embedded_at     TIMESTAMPTZ;

-- HNSW (Hierarchical Navigable Small World) index for approximate nearest
-- neighbour search. vector_cosine_ops pairs with the <=> (cosine distance)
-- operator used by the query. m / ef_construction trade build time and memory
-- for recall; these are sensible defaults for ~1536-dim embeddings.
CREATE INDEX idx_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Lets the embedding worker cheaply find chunks still awaiting an embedding.
CREATE INDEX idx_chunks_unembedded
    ON document_chunks (document_id)
    WHERE embedding IS NULL;
