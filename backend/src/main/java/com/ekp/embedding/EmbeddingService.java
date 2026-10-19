package com.ekp.embedding;

import com.ekp.embedding.ChunkVectorRepository.UnembeddedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Embeds chunks in batches and embeds ad-hoc query text. Embedding is resumable
 * and idempotent: it only processes chunks whose embedding is still NULL, and
 * each batch's writes commit independently, so a failure mid-document leaves the
 * already-embedded chunks intact and the rest pending.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final ChunkVectorRepository vectors;
    private final EmbeddingClient client;
    private final EmbeddingProperties props;

    public EmbeddingService(ChunkVectorRepository vectors, EmbeddingClient client,
                            EmbeddingProperties props) {
        this.vectors = vectors;
        this.client = client;
        this.props = props;
    }

    public boolean isUsable() {
        return props.isUsable();
    }

    /** Embeds all pending chunks of a document, one batch (one API call) at a time. */
    public void embedDocument(UUID documentId) {
        if (!isUsable()) {
            log.info("Embeddings disabled; skipping document {}", documentId);
            return;
        }
        int embedded = 0;
        while (true) {
            List<UnembeddedChunk> batch = vectors.findUnembedded(documentId, props.batchSize());
            if (batch.isEmpty()) {
                break;
            }
            List<float[]> result = client.embed(batch.stream().map(c ->
                    (props.isLocalNomic() ? "search_document: " : "") + c.content()).toList());
            for (int i = 0; i < batch.size(); i++) {
                vectors.saveEmbedding(batch.get(i).id(), result.get(i), props.model());
            }
            embedded += batch.size();
        }
        if (embedded > 0) {
            log.info("Embedded {} chunk(s) for document {}", embedded, documentId);
        }
    }

    /** Embeds a single query string for similarity search. */
    public float[] embedQuery(String query) {
        return client.embed(List.of((props.isLocalNomic() ? "search_query: " : "") + query)).get(0);
    }
}
