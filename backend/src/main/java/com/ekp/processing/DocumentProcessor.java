package com.ekp.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Runs a document through the pipeline on the processing executor (off the
 * request thread). Claims the work first to avoid double-processing, then
 * delegates the transactional steps and converts any failure into a FAILED
 * status rather than a lost task.
 */
@Component
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentProcessingService pipeline;

    public DocumentProcessor(DocumentProcessingService pipeline) {
        this.pipeline = pipeline;
    }

    @Async(ProcessingConfig.EXECUTOR)
    public void process(UUID documentId) {
        if (!pipeline.claim(documentId)) {
            log.debug("Skipping {}: already processed or claimed", documentId);
            return;
        }
        try {
            pipeline.runPipeline(documentId);
        } catch (Exception e) {
            log.error("Processing failed for document {}", documentId, e);
            pipeline.markFailed(documentId, e.getMessage());
        }
    }
}
