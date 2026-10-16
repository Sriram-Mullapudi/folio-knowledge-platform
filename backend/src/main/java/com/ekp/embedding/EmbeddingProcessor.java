package com.ekp.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Runs embedding off the request/processing threads on a dedicated, deliberately
 * small pool — concurrency is paced by the rate limiter, so a couple of workers
 * is plenty. Failures are logged and leave chunks pending (re-triggerable),
 * rather than failing the document, since chunking itself already succeeded.
 */
@Component
public class EmbeddingProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProcessor.class);

    private final EmbeddingService service;

    public EmbeddingProcessor(EmbeddingService service) {
        this.service = service;
    }

    @Async(EmbeddingConfig.EXECUTOR)
    public void embedDocument(UUID documentId) {
        try {
            service.embedDocument(documentId);
        } catch (Exception e) {
            log.error("Embedding failed for document {}", documentId, e);
        }
    }
}
