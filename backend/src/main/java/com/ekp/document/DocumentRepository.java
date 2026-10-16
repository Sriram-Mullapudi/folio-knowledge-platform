package com.ekp.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByUploadedBy(UUID uploadedBy, Pageable pageable);

    Page<Document> findByUploadedByAndCollectionId(UUID uploadedBy, UUID collectionId, Pageable pageable);

    boolean existsByUploadedByAndContentHash(UUID uploadedBy, String contentHash);

    // Ownership-scoped lookup: a user can only fetch/delete their own documents.
    Optional<Document> findByIdAndUploadedBy(UUID id, UUID uploadedBy);

    // Used by startup recovery to re-enqueue work left unfinished by a crash.
    List<Document> findByStatusIn(Collection<DocumentStatus> statuses);

    /**
     * Atomically claim a document for processing. Returns the number of rows
     * updated (1 = claimed by us, 0 = already COMPLETED or claimed elsewhere),
     * which prevents two workers from processing the same document at once.
     */
    @Modifying
    @Query("update Document d set d.status = :to "
            + "where d.id = :id and d.status <> :done")
    int claimForProcessing(@Param("id") UUID id,
                           @Param("to") DocumentStatus to,
                           @Param("done") DocumentStatus done);
}
