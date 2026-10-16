package com.ekp.processing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Enqueues processing only after the upload transaction commits, so the worker
 * never races ahead of the document row being visible. (A plain event listener
 * could fire before commit and find nothing.)
 */
@Component
public class DocumentUploadedListener {

    private final DocumentProcessor processor;

    public DocumentUploadedListener(DocumentProcessor processor) {
        this.processor = processor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        processor.process(event.documentId());
    }
}
