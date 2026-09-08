package com.amit.jobagent.document;

import java.time.Instant;
import java.util.UUID;

/** Immutable view of the active resume used while creating a candidate snapshot. */
public record ActiveResumeEvidence(
        UUID documentId,
        String checksum,
        String fileName,
        String extractedText,
        Instant createdAt) {}
