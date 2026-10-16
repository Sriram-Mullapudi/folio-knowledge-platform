package com.ekp.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One passage of a document's extracted text — the unit that will later be
 * embedded and retrieved. {@code chunkIndex} preserves reading order;
 * {@code charCount}/{@code tokenEstimate} are metadata for cost/size budgeting.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private String content;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentChunk() {
        // for JPA
    }

    public DocumentChunk(UUID documentId, int chunkIndex, String content,
                         int charCount, int tokenEstimate) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.charCount = charCount;
        this.tokenEstimate = tokenEstimate;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public int getCharCount() {
        return charCount;
    }

    public int getTokenEstimate() {
        return tokenEstimate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
