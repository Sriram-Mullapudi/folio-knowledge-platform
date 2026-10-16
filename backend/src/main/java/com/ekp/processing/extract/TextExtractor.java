package com.ekp.processing.extract;

/**
 * Strategy for turning raw file bytes into plain text. One implementation per
 * supported format; {@link TextExtractionService} dispatches by content type.
 */
public interface TextExtractor {

    /** Canonical content type this extractor handles (e.g. {@code application/pdf}). */
    String contentType();

    /** Extracts plain UTF-8 text from the file bytes. */
    String extract(byte[] bytes) throws Exception;
}
