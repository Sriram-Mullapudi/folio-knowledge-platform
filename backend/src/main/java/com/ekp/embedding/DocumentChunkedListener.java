package com.ekp.embedding;

import com.ekp.processing.DocumentChunkedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kicks off embedding once a document's chunks are committed. */
@Component
public class DocumentChunkedListener {

    private final EmbeddingProcessor processor;

    public DocumentChunkedListener(EmbeddingProcessor processor) {
        this.processor = processor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentChunked(DocumentChunkedEvent event) {
        processor.embedDocument(event.documentId());
    }
}
