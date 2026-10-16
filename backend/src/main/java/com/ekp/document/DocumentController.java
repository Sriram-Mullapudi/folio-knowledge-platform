package com.ekp.document;

import com.ekp.document.DocumentService.DownloadResult;
import com.ekp.document.dto.ChunkResponse;
import com.ekp.document.dto.DocumentResponse;
import com.ekp.document.dto.PageResponse;
import com.ekp.security.AuthenticatedUser;
import com.ekp.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Document ingestion API. All endpoints require an authenticated user and are
 * scoped to that user's own documents.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Upload, list, download and delete documents")
@SecurityRequirement(name = "bearer")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document (PDF, TXT or Markdown)")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        DocumentResponse created = service.upload(file, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List the current user's documents (newest first)")
    public PageResponse<DocumentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required=false) UUID collectionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user, page, size, collectionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single document's metadata and processing status")
    public DocumentResponse get(@PathVariable UUID id,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return service.get(id, user);
    }

    @GetMapping("/{id}/chunks")
    @Operation(summary = "List the chunks produced for a document")
    public PageResponse<ChunkResponse> chunks(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.listChunks(id, user, page, size);
    }

    @PostMapping("/{id}/reprocess")
    @Operation(summary = "Reset and re-run processing for a document")
    public DocumentResponse reprocess(@PathVariable UUID id,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return service.reprocess(id, user);
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download the original file bytes")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        DownloadResult result = service.download(id, user);
        StorageService.StorageObject object = result.object();
        String filename = URLEncoder.encode(result.document().getOriginalFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(object.contentType()))
                .contentLength(object.size())
                .body(new InputStreamResource(object.stream()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and its stored bytes")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
