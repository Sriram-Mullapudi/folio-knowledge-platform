package com.ekp.processing;

import java.util.UUID;

/** Published after a document's metadata is committed, to trigger processing. */
public record DocumentUploadedEvent(UUID documentId) {}
