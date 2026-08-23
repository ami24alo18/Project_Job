package com.amit.jobagent.document;
import java.time.Instant;
import java.util.UUID;
public record ResumeDocumentResponse(UUID id,UUID profileId,DocumentType documentType,String originalFileName,String sanitizedFileName,
 String contentType,long sizeBytes,String sha256Checksum,DocumentStatus status,boolean active,boolean extractedTextAvailable,
 String extractionError,long recordVersion,Instant createdAt,Instant updatedAt){}
