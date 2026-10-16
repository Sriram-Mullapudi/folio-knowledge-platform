package com.ekp.processing;

import java.util.UUID;

/** Published after a document's chunks are committed, to trigger embedding. */
public record DocumentChunkedEvent(UUID documentId) {}
