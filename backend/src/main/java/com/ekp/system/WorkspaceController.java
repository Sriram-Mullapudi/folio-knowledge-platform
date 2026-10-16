package com.ekp.system;
import com.ekp.security.AuthenticatedUser;
import com.ekp.embedding.EmbeddingService;
import com.ekp.rag.GenerationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceController {
    private final JdbcTemplate jdbc;
    private final EmbeddingService embeddings;
    private final GenerationProperties generation;
    public WorkspaceController(JdbcTemplate jdbc, EmbeddingService embeddings, GenerationProperties generation) {
        this.jdbc=jdbc; this.embeddings=embeddings; this.generation=generation;
    }
    @GetMapping("/summary") public Map<String,Object> summary(@AuthenticationPrincipal AuthenticatedUser user) {
        Map<String,Object> result=new LinkedHashMap<>(jdbc.queryForMap("""
            SELECT count(*) AS documents, COALESCE(sum(size_bytes),0) AS bytes,
            count(*) FILTER (WHERE status='COMPLETED') AS ready,
            count(*) FILTER (WHERE status='FAILED') AS failed,
            count(*) FILTER (WHERE status IN ('UPLOADED','PROCESSING')) AS processing,
            COALESCE(sum(chunk_count),0) AS passages
            FROM documents WHERE uploaded_by=?
            """,user.id()));
        result.put("searchMode", embeddings.isUsable() ? "hybrid" : "keyword");
        result.put("answersEnabled", generation.isUsable());
        return result;
    }
}
