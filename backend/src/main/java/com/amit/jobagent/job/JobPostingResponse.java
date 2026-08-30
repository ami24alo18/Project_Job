package com.amit.jobagent.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JobPostingResponse(
        UUID id, UUID sourceId, JobSourceType sourceType, String externalId, UUID duplicateOfJobId,
        String company, String title, String location, String countryCode, WorkplaceType workplaceType,
        EmploymentType employmentType, String department, String team, String descriptionPlainText,
        boolean descriptionTruncated, String applyUrl, String sourceUrl, BigDecimal salaryMinimum,
        BigDecimal salaryMaximum, String salaryCurrency, SalaryInterval salaryInterval, Instant publishedAt,
        Instant sourceUpdatedAt, Instant expiresAt, Instant firstSeenAt, Instant lastSeenAt,
        int missingSuccessfulRunCount, String fingerprint, String contentHash, JobPostingStatus status,
        boolean manuallyEdited, boolean sourceUpdateAvailable, JobIngestionProvider ingestionProvider,
        String originPublisher, String discoveryQuery, UUID externalEventId, String extractionRecipeVersion,
        long recordVersion, Instant createdAt, Instant updatedAt) {}
