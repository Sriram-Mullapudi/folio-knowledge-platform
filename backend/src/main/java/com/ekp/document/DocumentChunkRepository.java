package com.ekp.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    Page<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId, Pageable pageable);

    long countByDocumentId(UUID documentId);

    // Derived bulk delete so re-processing is idempotent (clear, then re-insert).
    // Runs inside the caller's transaction (runPipeline).
    void deleteByDocumentId(UUID documentId);
}
