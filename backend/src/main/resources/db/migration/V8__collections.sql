CREATE TABLE document_collections (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 name VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_collection_owner_name ON document_collections(owner_id,lower(name));
ALTER TABLE documents ADD COLUMN collection_id UUID REFERENCES document_collections(id) ON DELETE SET NULL;
CREATE INDEX idx_documents_collection ON documents(collection_id);
