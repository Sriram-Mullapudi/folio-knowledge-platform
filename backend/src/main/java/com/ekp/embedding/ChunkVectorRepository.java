package com.ekp.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All vector-specific persistence lives here, behind plain JDBC. The {@code
 * vector} column is never mapped by JPA — vectors are written and queried as
 * pgvector text literals cast with {@code CAST(? AS vector)}, so Hibernate (in
 * validate mode) simply ignores the unmapped column.
 */
@Repository
public class ChunkVectorRepository {

    private final JdbcTemplate jdbc;

    public ChunkVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Chunks for a document that still need an embedding, in reading order. */
    public List<UnembeddedChunk> findUnembedded(UUID documentId, int limit) {
        return jdbc.query(
                "SELECT id, content FROM document_chunks "
                        + "WHERE document_id = ? AND embedding IS NULL "
                        + "ORDER BY chunk_index LIMIT ?",
                (rs, i) -> new UnembeddedChunk(UUID.fromString(rs.getString("id")),
                        rs.getString("content")),
                documentId, limit);
    }

    public int countUnembedded(UUID documentId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM document_chunks "
                        + "WHERE document_id = ? AND embedding IS NULL",
                Integer.class, documentId);
        return count == null ? 0 : count;
    }

    /** Distinct documents that still have at least one un-embedded chunk. */
    public List<UUID> documentsWithUnembeddedChunks() {
        return jdbc.queryForList(
                        "SELECT DISTINCT document_id FROM document_chunks WHERE embedding IS NULL",
                        String.class)
                .stream().map(UUID::fromString).toList();
    }

    public void saveEmbedding(UUID chunkId, float[] vector, String model) {
        jdbc.update(
                "UPDATE document_chunks "
                        + "SET embedding = CAST(? AS vector), embedding_model = ?, embedded_at = now() "
                        + "WHERE id = ?",
                Vectors.toLiteral(vector), model, chunkId);
    }

    /**
     * Top-k cosine-similarity search over the caller's chunks. Ordering by the
     * {@code <=>} operator lets Postgres use the HNSW index. Ownership is enforced
     * by joining documents; an optional documentId narrows to one document.
     */
    public List<SearchHit> search(float[] queryVector, UUID ownerId, UUID documentId, int k) {
        return search(queryVector,ownerId,documentId,k,null);
    }
    public List<SearchHit> search(float[] queryVector,UUID ownerId,UUID documentId,int k,UUID collectionId) {
        String literal = Vectors.toLiteral(queryVector);
        StringBuilder sql = new StringBuilder(
                "SELECT c.id, c.document_id, c.chunk_index, c.content, "
                        + "c.embedding <=> CAST(? AS vector) AS distance "
                        + "FROM document_chunks c "
                        + "JOIN documents d ON d.id = c.document_id "
                        + "WHERE d.uploaded_by = ? AND d.status = 'COMPLETED' AND c.embedding IS NOT NULL ");
        List<Object> args = new ArrayList<>();
        args.add(literal);
        args.add(ownerId);
        if (documentId != null) {
            sql.append("AND c.document_id = ? ");
            args.add(documentId);
        }
        if(collectionId!=null) { sql.append("AND d.collection_id = ? ");args.add(collectionId); }
        sql.append("ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?");
        args.add(literal);
        args.add(k);

        return jdbc.query(sql.toString(), HIT_MAPPER, args.toArray());
    }

    private static final RowMapper<SearchHit> HIT_MAPPER = (rs, i) -> {
        double distance = rs.getDouble("distance");
        return new SearchHit(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("document_id")),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                distance,
                1.0 - distance); // cosine similarity
    };

    public record UnembeddedChunk(UUID id, String content) {}

    public record SearchHit(UUID chunkId, UUID documentId, int chunkIndex, String content,
                            double distance, double similarity) {}
}
