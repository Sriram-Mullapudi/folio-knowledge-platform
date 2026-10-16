package com.ekp.audit;

/**
 * Auditable actions. Authentication events are covered in Phase 1; ingestion and
 * retrieval actions (UPLOAD, SEARCH, ANSWER) are added in their phases.
 */
public enum AuditAction {
    REGISTER,
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    DOCUMENT_UPLOAD,
    DOCUMENT_DELETE,
    ANSWER
}
