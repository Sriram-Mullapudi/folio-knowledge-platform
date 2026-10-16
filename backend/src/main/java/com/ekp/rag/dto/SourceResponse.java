package com.ekp.rag.dto;

import java.util.UUID;

/**
 * Citation metadata for one retrieved chunk. {@code number} is the inline
 * citation marker ([1], [2], ...) the model is told to use, so the UI can map a
 * marker back to its source.
 *
 * @param number     1-based citation index used in the prompt and the answer
 * @param documentId source document
 * @param filename   source document's display name
 * @param chunkIndex position of the chunk within the document
 * @param similarity cosine similarity to the query (1.0 = identical)
 * @param snippet    the chunk text (the evidence the answer is grounded in)
 */
public record SourceResponse(
        int number,
        UUID documentId,
        String filename,
        int chunkIndex,
        double similarity,
        String snippet
) {}
