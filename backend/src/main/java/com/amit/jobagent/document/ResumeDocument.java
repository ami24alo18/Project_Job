package com.amit.jobagent.document;

import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.persistence.MutableEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "resume_document")
class ResumeDocument extends MutableEntity {
    @Column(name = "profile_id", nullable = false, updatable = false)
    private UUID profileId;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;
    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;
    @Column(name = "sanitized_file_name", nullable = false)
    private String sanitizedFileName;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256_checksum", nullable = false, length = 64, columnDefinition = "char(64)")
    private String sha256Checksum;
    @Column(name = "storage_key", nullable = false, length = 700)
    private String storageKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;
    @Column(name = "extraction_error", length = 500)
    private String extractionError;

    protected ResumeDocument() {
    }

    ResumeDocument(UUID id, UUID profileId, String original, String sanitized, String type, long size, String checksum, String key) {
        this.id = id;
        this.profileId = profileId;
        documentType = DocumentType.MASTER_RESUME;
        originalFileName = original;
        sanitizedFileName = sanitized;
        contentType = type;
        sizeBytes = size;
        sha256Checksum = checksum;
        storageKey = key;
        status = DocumentStatus.UPLOADED;
    }

    void extracted(String text) {
        extractedText = text;
        extractionError = null;
        status = DocumentStatus.TEXT_EXTRACTED;
    }

    void extractionFailed() {
        extractedText = null;
        extractionError = "Text extraction failed";
        status = DocumentStatus.EXTRACTION_FAILED;
    }

    void activate() {
        if (status == DocumentStatus.ARCHIVED)
            throw new DomainValidationException("Archived documents cannot be activated");
        active = true;
    }

    void deactivate() {
        active = false;
    }

    void archive() {
        if (status == DocumentStatus.ARCHIVED) throw new DomainValidationException("Document is already archived");
        active = false;
        status = DocumentStatus.ARCHIVED;
    }

    UUID profileId() {
        return profileId;
    }

    DocumentType documentType() {
        return documentType;
    }

    String originalFileName() {
        return originalFileName;
    }

    String sanitizedFileName() {
        return sanitizedFileName;
    }

    String contentType() {
        return contentType;
    }

    long sizeBytes() {
        return sizeBytes;
    }

    String sha256Checksum() {
        return sha256Checksum;
    }

    String storageKey() {
        return storageKey;
    }

    DocumentStatus status() {
        return status;
    }

    boolean active() {
        return active;
    }

    String extractedText() {
        return extractedText;
    }

    String extractionError() {
        return extractionError;
    }
}
