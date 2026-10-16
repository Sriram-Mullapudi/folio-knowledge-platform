package com.ekp.document;

/**
 * Document processing lifecycle:
 * <pre>
 *   UPLOADED ──▶ PROCESSING ──▶ COMPLETED
 *                     │
 *                     └────────▶ FAILED   (recoverable via reprocess)
 * </pre>
 * A document is UPLOADED the moment its bytes are stored. An async worker then
 * moves it to PROCESSING, and on to COMPLETED (chunks persisted) or FAILED.
 */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    COMPLETED,
    FAILED
}
