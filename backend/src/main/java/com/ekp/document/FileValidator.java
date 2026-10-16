package com.ekp.document;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Validates uploads before they are stored. Defends in depth: it checks the
 * extension, enforces a size cap, and sniffs the actual bytes so a renamed or
 * mislabeled file (e.g. an executable called {@code report.pdf}) is rejected.
 * The returned value is the canonical content type we persist, rather than the
 * client-supplied one which is easily spoofed.
 */
@Component
public class FileValidator {

    // Extension -> canonical content type we trust and store.
    private static final Map<String, String> ALLOWED = Map.of(
            "pdf", "application/pdf",
            "txt", "text/plain",
            "md", "text/markdown",
            "markdown", "text/markdown"
    );

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final int SNIFF_LIMIT = 8192;

    private final long maxBytes;

    public FileValidator(DocumentProperties props) {
        this.maxBytes = props.maxFileSize().toBytes();
    }

    /**
     * @return the canonical content type to store
     * @throws IllegalArgumentException (-> HTTP 400) if the upload is rejected
     */
    public String validate(String originalFilename, byte[] bytes) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException(
                    "File exceeds the maximum allowed size of " + maxBytes + " bytes");
        }

        String ext = extension(originalFilename);
        String canonical = ALLOWED.get(ext);
        if (canonical == null) {
            throw new IllegalArgumentException(
                    "Unsupported file type '." + ext + "'. Allowed: PDF, TXT, Markdown");
        }

        switch (canonical) {
            case "application/pdf" -> requirePdfMagic(bytes);
            case "text/plain", "text/markdown" -> requireText(bytes);
            default -> { /* unreachable: ALLOWED is closed */ }
        }
        return canonical;
    }

    private void requirePdfMagic(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            throw new IllegalArgumentException("File is not a valid PDF");
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                throw new IllegalArgumentException("File content is not a valid PDF");
            }
        }
    }

    /** Reject obviously-binary content masquerading as text (NUL byte heuristic). */
    private void requireText(byte[] bytes) {
        int limit = Math.min(bytes.length, SNIFF_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0x00) {
                throw new IllegalArgumentException("File does not appear to be text");
            }
        }
    }

    private String extension(String filename) {
        // Strip any path the client may have included, then take the suffix.
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new IllegalArgumentException("File must have an extension");
        }
        return name.substring(dot + 1).toLowerCase();
    }
}
