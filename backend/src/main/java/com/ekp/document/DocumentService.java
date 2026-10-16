package com.ekp.document;

import com.ekp.audit.AuditAction;
import com.ekp.audit.AuditService;
import com.ekp.common.error.ConflictException;
import com.ekp.common.error.ResourceNotFoundException;
import com.ekp.document.dto.ChunkResponse;
import com.ekp.document.dto.DocumentResponse;
import com.ekp.document.dto.PageResponse;
import com.ekp.processing.DocumentUploadedEvent;
import com.ekp.security.AuthenticatedUser;
import com.ekp.storage.StorageException;
import com.ekp.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentRepository repository;
    private final DocumentChunkRepository chunkRepository;
    private final StorageService storage;
    private final FileValidator validator;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public DocumentService(DocumentRepository repository, DocumentChunkRepository chunkRepository,
                           StorageService storage, FileValidator validator, AuditService audit,
                           ApplicationEventPublisher events) {
        this.repository = repository;
        this.chunkRepository = chunkRepository;
        this.storage = storage;
        this.validator = validator;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public DocumentResponse upload(MultipartFile file, AuthenticatedUser user) {
        byte[] bytes = readBytes(file);
        String filename = file.getOriginalFilename();

        // 1. Validate (throws 400) and resolve the trusted content type.
        String contentType = validator.validate(filename, bytes);
        String hash = sha256Hex(bytes);

        // 2. Fast duplicate pre-check (nice 409 without touching storage).
        if (repository.existsByUploadedByAndContentHash(user.id(), hash)) {
            throw new ConflictException("You have already uploaded this document");
        }

        // 3. Store bytes, then persist metadata. If persistence fails (including
        //    a lost race on the unique index), delete the just-written object so
        //    storage never accumulates orphans.
        String key = buildStorageKey(user.id(), filename);
        storage.store(key, bytes, contentType);

        Document doc = new Document(filename, contentType, bytes.length, hash, key, user.id());
        try {
            repository.saveAndFlush(doc); // flush now so a unique violation surfaces here
        } catch (DataIntegrityViolationException race) {
            storage.delete(key);
            throw new ConflictException("You have already uploaded this document");
        } catch (RuntimeException ex) {
            storage.delete(key);
            throw ex;
        }

        audit.record(user.id(), AuditAction.DOCUMENT_UPLOAD, "document", doc.getId(),
                Map.of("filename", filename, "sizeBytes", bytes.length), null);

        // Trigger async processing once this transaction commits (see the
        // AFTER_COMMIT listener) so the worker never races the row's visibility.
        events.publishEvent(new DocumentUploadedEvent(doc.getId()));
        return DocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(AuthenticatedUser user, int page, int size) { return list(user,page,size,null); }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(AuthenticatedUser user, int page, int size, UUID collectionId) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Document> result = collectionId==null ? repository.findByUploadedBy(user.id(), pageable) : repository.findByUploadedByAndCollectionId(user.id(),collectionId,pageable);
        return PageResponse.of(result, DocumentResponse::from);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id, AuthenticatedUser user) {
        Document doc = repository.findByIdAndUploadedBy(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        return DocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChunkResponse> listChunks(UUID id, AuthenticatedUser user, int page, int size) {
        // Ownership check (404 if it isn't the caller's document).
        repository.findByIdAndUploadedBy(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        Page<DocumentChunk> result =
                chunkRepository.findByDocumentIdOrderByChunkIndexAsc(id, pageable);
        return PageResponse.of(result, ChunkResponse::from);
    }

    /** Reset and re-enqueue a document (e.g. after a FAILED run). */
    @Transactional
    public DocumentResponse reprocess(UUID id, AuthenticatedUser user) {
        Document doc = repository.findByIdAndUploadedBy(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setProcessingError(null);
        repository.save(doc);
        events.publishEvent(new DocumentUploadedEvent(doc.getId()));
        return DocumentResponse.from(doc);
    }

    @Transactional(readOnly = true)
    public DownloadResult download(UUID id, AuthenticatedUser user) {
        Document doc = repository.findByIdAndUploadedBy(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        StorageService.StorageObject object = storage.load(doc.getStorageKey());
        return new DownloadResult(doc, object);
    }

    @Transactional
    public void delete(UUID id, AuthenticatedUser user) {
        Document doc = repository.findByIdAndUploadedBy(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        repository.delete(doc);
        storage.delete(doc.getStorageKey());
        audit.record(user.id(), AuditAction.DOCUMENT_DELETE, "document", id,
                Map.of("filename", doc.getOriginalFilename()), null);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new StorageException("Could not read uploaded file", e);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** {@code documents/<userId>/<uuid>/<sanitized-filename>} — collision-free and traceable. */
    private String buildStorageKey(UUID userId, String filename) {
        return "documents/" + userId + "/" + UUID.randomUUID() + "/" + sanitize(filename);
    }

    private String sanitize(String filename) {
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    public record DownloadResult(Document document, StorageService.StorageObject object) {}
}
