package com.ekp.document.dto;

import com.ekp.document.Document;

import java.time.Instant;
import java.util.UUID;

/** Client-safe view of a document. Excludes the internal storage key. */
public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String status,
        Integer chunkCount,
        Instant uploadedAt,
        Instant processedAt,
        String processingError,
        UUID uploadedBy,
        UUID collectionId
) {
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                doc.getStatus().name(),
                doc.getChunkCount(),
                doc.getCreatedAt(),
                doc.getProcessedAt(),
                doc.getProcessingError(),
                doc.getUploadedBy(),
                doc.getCollectionId()
        );
    }
}
