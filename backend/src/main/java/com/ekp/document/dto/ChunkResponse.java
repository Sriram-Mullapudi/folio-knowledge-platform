package com.ekp.document.dto;

import com.ekp.document.DocumentChunk;

import java.util.UUID;

/** Client-safe view of a single chunk. */
public record ChunkResponse(
        UUID id,
        int index,
        String content,
        int charCount,
        int tokenEstimate
) {
    public static ChunkResponse from(DocumentChunk chunk) {
        return new ChunkResponse(
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getCharCount(),
                chunk.getTokenEstimate()
        );
    }
}
