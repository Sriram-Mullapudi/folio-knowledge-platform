package com.ekp.conversation;

import com.ekp.common.error.ResourceNotFoundException;
import com.ekp.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public ConversationController(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }
    public record Source(int number, @NotNull UUID documentId, @NotBlank @Size(max=500) String filename,
                         int chunkIndex, double similarity, @NotNull @Size(max=50000) String snippet) {}
    public record Turn(@NotBlank @Size(max=100) String id, @NotBlank @Size(max=4000) String question,
                       @NotNull @Size(max=100000) String answer, @NotNull @Size(max=30) List<@Valid Source> sources,
                       @NotBlank @Pattern(regexp="SEARCH|RETRIEVAL|RESEARCH|SUMMARIZATION") String kind,
                       @Size(max=4000) String error, @Size(max=30) List<@Size(max=4000) String> trace) {}
    public record Snapshot(@NotBlank @Size(max=120) String title, @NotNull @Size(min=1,max=100) List<@Valid Turn> turns,
                           @NotNull @Pattern(regexp="SEARCH|RETRIEVAL|RESEARCH|SUMMARIZATION") String mode,
                           @NotNull @Size(max=36) String scope, UUID collectionId) {}
    @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @RequestParam(defaultValue="0") int page) {
        if(page<0||page>10000) throw new IllegalArgumentException("Invalid page");
        return jdbc.queryForList("SELECT id,title,updated_at AS \"updatedAt\" FROM saved_conversations WHERE owner_id=? ORDER BY updated_at DESC,id LIMIT 30 OFFSET ?",user.id(),page*30);
    }
    @GetMapping("/{id}") public Object read(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser user) throws Exception {
        var rows=jdbc.queryForList("SELECT snapshot::text FROM saved_conversations WHERE id=? AND owner_id=?",String.class,id,user.id());
        if(rows.isEmpty()) throw new ResourceNotFoundException("Conversation not found");
        return mapper.readTree(rows.get(0));
    }
    @PostMapping public Map<String,UUID> create(@Valid @RequestBody Snapshot snapshot,@AuthenticationPrincipal AuthenticatedUser user) throws Exception {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO saved_conversations(id,owner_id,title,snapshot) VALUES (?,?,?,?::jsonb)",id,user.id(),snapshot.title(),encode(snapshot));
        return Map.of("id",id);
    }
    @PostMapping("/{id}") public Map<String,UUID> update(@PathVariable UUID id,@Valid @RequestBody Snapshot snapshot,@AuthenticationPrincipal AuthenticatedUser user) throws Exception {
        int count=jdbc.update("UPDATE saved_conversations SET title=?,snapshot=?::jsonb,updated_at=now() WHERE id=? AND owner_id=?",snapshot.title(),encode(snapshot),id,user.id());
        if(count==0) throw new ResourceNotFoundException("Conversation not found");
        return Map.of("id",id);
    }
    private String encode(Snapshot snapshot) throws Exception {
        String json=mapper.writeValueAsString(snapshot);
        if(json.length()>1000000) throw new IllegalArgumentException("This conversation is too large to save. Start a new conversation.");
        return json;
    }
}
