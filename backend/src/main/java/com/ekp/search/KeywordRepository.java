package com.ekp.search;

import com.ekp.embedding.ChunkVectorRepository.SearchHit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class KeywordRepository {
    private final JdbcTemplate jdbc;
    public KeywordRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<SearchHit> search(String query, UUID owner, UUID document, int limit) {
        return search(query,owner,document,limit,null);
    }
    public List<SearchHit> search(String query, UUID owner, UUID document, int limit, UUID collection) {
        var exact = query(query, owner, document, limit, false, collection);
        return exact.isEmpty() ? query(query, owner, document, limit, true, collection) : exact;
    }

    private List<SearchHit> query(String query, UUID owner, UUID document, int limit, boolean broad, UUID collection) {
        String sql = """
            SELECT c.id, c.document_id, c.chunk_index, c.content,
                   ts_rank_cd(c.search_vector, plainto_tsquery('english', ?)) AS rank
            FROM document_chunks c JOIN documents d ON d.id = c.document_id
            WHERE d.uploaded_by = ? AND d.status = 'COMPLETED'
              AND (CAST(? AS uuid) IS NULL OR c.document_id = ?)
              AND (CAST(? AS uuid) IS NULL OR d.collection_id = ?)
              AND c.search_vector @@ plainto_tsquery('english', ?)
            ORDER BY rank DESC, c.id LIMIT ?
            """;
        // Broaden parsed lexemes only when the all-terms query has no results.
        // The user's text remains a bound parameter in both cases.
        if (broad) sql=sql.replace("plainto_tsquery('english', ?)",
                "to_tsquery('english', replace(plainto_tsquery('english', ?)::text, ' & ', ' | '))");
        return jdbc.query(sql, (rs, i) -> new SearchHit(rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class), rs.getInt("chunk_index"),
                rs.getString("content"), 0, rs.getDouble("rank")),
                query, owner, document, document, collection, collection, query, limit);
    }
}
