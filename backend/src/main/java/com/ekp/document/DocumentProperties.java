package com.ekp.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Document module settings ({@code ekp.documents.*}).
 *
 * @param maxFileSize   upload cap (also enforced at the multipart layer)
 * @param chunkSize     target chunk size in characters (~4 chars per token)
 * @param chunkOverlap  characters of overlap carried between adjacent chunks
 */
@ConfigurationProperties(prefix = "ekp.documents")
public record DocumentProperties(
        DataSize maxFileSize,
        int chunkSize,
        int chunkOverlap
) {}
