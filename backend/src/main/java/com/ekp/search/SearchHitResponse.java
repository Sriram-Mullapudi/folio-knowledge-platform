package com.ekp.search;

import com.ekp.embedding.ChunkVectorRepository.SearchHit;

import java.util.UUID;

/** A retrieved chunk. The legacy similarity field carries an RRF ranking weight, not a probability. */
public record SearchHitResponse(
        UUID chunkId,
        UUID documentId,
        int chunkIndex,
        String content,
        double similarity
) {
    public static SearchHitResponse from(SearchHit hit) {
        return new SearchHitResponse(
                hit.chunkId(),
                hit.documentId(),
                hit.chunkIndex(),
                hit.content(),
                hit.similarity());
    }
}
