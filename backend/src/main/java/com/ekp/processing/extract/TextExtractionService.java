package com.ekp.processing.extract;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes a document to the right {@link TextExtractor} by content type. The
 * extractors are discovered from the Spring context, so adding a new format is
 * just adding a new {@code @Component} — no change here.
 */
@Service
public class TextExtractionService {

    private final Map<String, TextExtractor> byContentType;

    public TextExtractionService(List<TextExtractor> extractors) {
        this.byContentType = extractors.stream()
                .collect(Collectors.toUnmodifiableMap(TextExtractor::contentType, Function.identity()));
    }

    public String extract(String contentType, byte[] bytes) throws Exception {
        TextExtractor extractor = byContentType.get(contentType);
        if (extractor == null) {
            throw new IllegalStateException("No extractor for content type: " + contentType);
        }
        return extractor.extract(bytes);
    }
}
