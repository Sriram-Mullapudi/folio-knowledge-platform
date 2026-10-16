package com.ekp.collection;
import com.ekp.security.AuthenticatedUser;
import com.ekp.common.error.ResourceNotFoundException;
import com.ekp.common.error.ConflictException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {
 private final JdbcTemplate jdbc;
 public CollectionController(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Name(@NotBlank @Size(max=80) String name) {}
 public record Placement(UUID collectionId) {}
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal AuthenticatedUser user){
  return jdbc.queryForList("SELECT f.id,f.name,count(d.id) AS count FROM document_collections f LEFT JOIN documents d ON d.collection_id=f.id AND d.uploaded_by=f.owner_id WHERE f.owner_id=? GROUP BY f.id ORDER BY lower(f.name),f.id",user.id());
 }
 @PostMapping public Map<String,UUID> create(@Valid @RequestBody Name name,@AuthenticationPrincipal AuthenticatedUser user){
  UUID id=UUID.randomUUID();
  try{jdbc.update("INSERT INTO document_collections(id,owner_id,name) VALUES (?,?,?)",id,user.id(),name.name().strip());}
  catch(DuplicateKeyException e){throw new ConflictException("A collection with that name already exists.");}
  return Map.of("id",id);
 }
 @PostMapping("/{id}") public Map<String,UUID> rename(@PathVariable UUID id,@Valid @RequestBody Name name,@AuthenticationPrincipal AuthenticatedUser user){
  try{if(jdbc.update("UPDATE document_collections SET name=? WHERE id=? AND owner_id=?",name.name().strip(),id,user.id())==0)throw new ResourceNotFoundException("Collection not found");}
  catch(DuplicateKeyException e){throw new ConflictException("A collection with that name already exists.");}
  return Map.of("id",id);
 }
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void remove(@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser user){
  if(jdbc.update("DELETE FROM document_collections WHERE id=? AND owner_id=?",id,user.id())==0)throw new ResourceNotFoundException("Collection not found");
 }
 @PostMapping("/documents/{id}") public Map<String,UUID> move(@PathVariable UUID id,@RequestBody Placement placement,@AuthenticationPrincipal AuthenticatedUser user){
  int changed=jdbc.update("UPDATE documents SET collection_id=? WHERE id=? AND uploaded_by=? AND (CAST(? AS uuid) IS NULL OR EXISTS(SELECT 1 FROM document_collections WHERE id=? AND owner_id=?))",placement.collectionId(),id,user.id(),placement.collectionId(),placement.collectionId(),user.id());
  if(changed==0)throw new ResourceNotFoundException("Document or collection not found");
  return Map.of("id",id);
 }
}
