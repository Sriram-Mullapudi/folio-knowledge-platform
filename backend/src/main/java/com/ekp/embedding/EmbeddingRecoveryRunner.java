package com.ekp.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * On startup, re-enqueue embedding for any document that still has un-embedded
 * chunks (e.g. uploaded while the key was missing, or interrupted by a crash).
 * Ordered after processing recovery so freshly re-chunked documents are caught.
 */
@Component
@Order(20)
public class EmbeddingRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingRecoveryRunner.class);

    private final ChunkVectorRepository vectors;
    private final EmbeddingProcessor processor;
    private final EmbeddingService service;

    public EmbeddingRecoveryRunner(ChunkVectorRepository vectors, EmbeddingProcessor processor,
                                   EmbeddingService service) {
        this.vectors = vectors;
        this.processor = processor;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!service.isUsable()) {
            return;
        }
        List<UUID> pending = vectors.documentsWithUnembeddedChunks();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Recovering embeddings for {} document(s)", pending.size());
        for (UUID documentId : pending) {
            processor.embedDocument(documentId);
        }
    }
}
