package com.ekp.processing;

import com.ekp.document.Document;
import com.ekp.document.DocumentRepository;
import com.ekp.document.DocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup, re-enqueue any documents left UPLOADED (never picked up) or
 * PROCESSING (a worker died mid-task before the previous run). Because the
 * pipeline is idempotent, re-running is safe. The in-process queue is not
 * durable across restarts; persisted status is what makes recovery possible.
 */
@Component
public class ProcessingRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessingRecoveryRunner.class);

    private final DocumentRepository documents;
    private final DocumentProcessor processor;

    public ProcessingRecoveryRunner(DocumentRepository documents, DocumentProcessor processor) {
        this.documents = documents;
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Document> pending = documents.findByStatusIn(
                List.of(DocumentStatus.UPLOADED, DocumentStatus.PROCESSING));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Recovering {} unfinished document(s) for processing", pending.size());
        for (Document document : pending) {
            processor.process(document.getId());
        }
    }
}
