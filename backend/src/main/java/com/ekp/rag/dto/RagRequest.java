package com.ekp.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * A question to answer over the user's documents.
 *
 * @param question   natural-language question
 * @param documentId optional filter to a single document
 * @param k          optional retrieval depth (clamped server-side)
 */
public record RagRequest(
        @NotBlank @Size(max = 4000) String question,
        UUID documentId,
        Integer k,
        UUID collectionId,
        @Size(max = 3) List<@NotNull @Valid PreviousTurn> history
) {
    public record PreviousTurn(@NotBlank @Size(max = 500) String question,
                               @NotBlank @Size(max = 1000) String answer) {}
    public RagRequest(String question, UUID documentId, Integer k, UUID collectionId) {
        this(question, documentId, k, collectionId, List.of());
    }
    public RagRequest(String question, UUID documentId, Integer k) { this(question,documentId,k,null); }
}
