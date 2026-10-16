package com.ekp.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Similarity search request.
 *
 * @param query      natural-language query to embed and match
 * @param documentId optional filter to a single document
 * @param k          number of results to return (clamped server-side)
 */
public record SearchRequest(
        @NotBlank @Size(max = 4000) String query,
        UUID documentId,
        Integer k,
        UUID collectionId
) { public SearchRequest(String query,UUID documentId,Integer k) { this(query,documentId,k,null); } }
