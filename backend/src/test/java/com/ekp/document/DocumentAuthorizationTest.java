package com.ekp.document;

import com.ekp.audit.AuditService;
import com.ekp.common.error.ResourceNotFoundException;
import com.ekp.security.AuthenticatedUser;
import com.ekp.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentAuthorizationTest {
    DocumentRepository documents;
    DocumentChunkRepository chunks;
    StorageService storage;
    AuditService audit;
    ApplicationEventPublisher events;
    DocumentService service;
    UUID owner = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();

    @BeforeEach void setup() {
        documents = mock(DocumentRepository.class);
        chunks = mock(DocumentChunkRepository.class);
        storage = mock(StorageService.class);
        audit = mock(AuditService.class);
        events = mock(ApplicationEventPublisher.class);
        service = new DocumentService(documents, chunks, storage, mock(FileValidator.class), audit, events);
    }

    @ParameterizedTest
    @CsvSource({"get,USER", "chunks,USER", "download,USER", "delete,USER", "reprocess,USER",
                "get,ADMIN", "chunks,ADMIN", "download,ADMIN", "delete,ADMIN", "reprocess,ADMIN"})
    void rejectsOtherOwnersWithoutSideEffects(String operation, String role) {
        var user = new AuthenticatedUser(UUID.randomUUID(), "other@example.test", role);
        when(documents.findByIdAndUploadedBy(documentId,user.id())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> {
            switch(operation) {
                case "get" -> service.get(documentId,user);
                case "chunks" -> service.listChunks(documentId,user,0,20);
                case "download" -> service.download(documentId,user);
                case "delete" -> service.delete(documentId,user);
                case "reprocess" -> service.reprocess(documentId,user);
                default -> throw new AssertionError(operation);
            }
        }).isInstanceOf(ResourceNotFoundException.class);
        verify(documents).findByIdAndUploadedBy(documentId,user.id());
        verifyNoMoreInteractions(documents);
        verifyNoInteractions(chunks,storage,audit,events);
    }

    @Test void listUsesCallerIdentityAndBoundsPagination() {
        var user = new AuthenticatedUser(owner,"owner@example.test","USER");
        when(documents.findByUploadedBy(eq(owner),any(Pageable.class))).thenAnswer(i -> Page.empty(i.getArgument(1)));
        var page = service.list(user,-10,100000);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(100);
        verify(documents).findByUploadedBy(eq(owner),any(Pageable.class));
        verifyNoInteractions(storage,chunks);
    }

    @Test void ownerCanReadMetadata() {
        var user = new AuthenticatedUser(owner,"owner@example.test","USER");
        var doc = new Document("private.txt","text/plain",5,"hash","owner/key",owner);
        doc.onCreate();
        when(documents.findByIdAndUploadedBy(doc.getId(),owner)).thenReturn(Optional.of(doc));
        assertThat(service.get(doc.getId(),user).id()).isEqualTo(doc.getId());
        verifyNoInteractions(storage);
    }
}
