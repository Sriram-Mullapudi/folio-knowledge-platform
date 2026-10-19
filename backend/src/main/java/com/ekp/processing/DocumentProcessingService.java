package com.ekp.processing;

import com.ekp.document.Document;
import com.ekp.document.DocumentChunk;
import com.ekp.document.DocumentChunkRepository;
import com.ekp.document.DocumentRepository;
import com.ekp.document.DocumentStatus;
import com.ekp.processing.chunk.RecursiveChunker;
import com.ekp.processing.extract.TextExtractionService;
import com.ekp.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The transactional steps of processing, kept separate from the async
 * orchestrator ({@link DocumentProcessor}) so Spring's {@code @Transactional}
 * proxies apply (self-invocation would bypass them).
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final StorageService storage;
    private final TextExtractionService extraction;
    private final RecursiveChunker chunker;
    private final ApplicationEventPublisher events;

    public DocumentProcessingService(DocumentRepository documents, DocumentChunkRepository chunks,
                                     StorageService storage, TextExtractionService extraction,
                                     RecursiveChunker chunker, ApplicationEventPublisher events) {
        this.documents = documents;
        this.chunks = chunks;
        this.storage = storage;
        this.extraction = extraction;
        this.chunker = chunker;
        this.events = events;
    }

    /** Atomically move the document to PROCESSING. False = already done/claimed. */
    @Transactional
    public boolean claim(UUID documentId) {
        return documents.claimForProcessing(documentId,
                DocumentStatus.PROCESSING, DocumentStatus.COMPLETED) > 0;
    }

    /**
     * Extract text, chunk it, and persist the chunks. Idempotent: existing
     * chunks are cleared first, so re-processing replaces rather than duplicates.
     * Any failure rolls the whole step back (no partial chunk sets).
     */
    @Transactional(rollbackFor = Exception.class)
    public void runPipeline(UUID documentId) throws Exception {
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document vanished: " + documentId));

        byte[] bytes = readBytes(document.getStorageKey());
        String text = extraction.extract(document.getContentType(), bytes);
        List<String> pieces = chunker.chunk(text);

        chunks.deleteByDocumentId(documentId);
        // Hibernate schedules inserts before entity deletes unless explicitly
        // flushed; replacement chunks reuse the unique document/index pair.
        chunks.flush();
        List<DocumentChunk> entities = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            entities.add(new DocumentChunk(documentId, i, piece, piece.length(), estimateTokens(piece)));
        }
        chunks.saveAll(entities);

        document.setStatus(DocumentStatus.COMPLETED);
        document.setChunkCount(pieces.size());
        document.setProcessedAt(Instant.now());
        document.setProcessingError(null);
        documents.save(document);

        // Trigger embedding once these chunks are committed (AFTER_COMMIT listener).
        events.publishEvent(new DocumentChunkedEvent(documentId));

        log.info("Processed document {}: {} chars -> {} chunks",
                documentId, text.length(), pieces.size());
    }

    /** Records a failure in its own transaction so it persists regardless. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID documentId, String error) {
        documents.findById(documentId).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setProcessingError(truncate(error));
            documents.save(doc);
        });
    }

    private byte[] readBytes(String storageKey) throws Exception {
        try (InputStream in = storage.load(storageKey).stream()) {
            return in.readAllBytes();
        }
    }

    /** Cheap heuristic: ~4 characters per token for English text. */
    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }

    private String truncate(String error) {
        if (error == null) {
            return "Unknown error";
        }
        return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
    }
}
