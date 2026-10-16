package com.ekp.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

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
        UUID collectionId
) { public RagRequest(String question,UUID documentId,Integer k) { this(question,documentId,k,null); } }
