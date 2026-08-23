package com.amit.jobagent.jobsource.connector;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Provider-neutral data extracted from a public feed. Description HTML remains
 * untrusted and must pass through the job module's sanitizer before persistence.
 */
public record RawJobRecord(
        String externalId,
        String company,
        String title,
        String location,
        String countryCode,
        String workplaceType,
        String employmentType,
        String department,
        String team,
        String descriptionPlainText,
        String descriptionHtml,
        String applyUrl,
        String sourceUrl,
        BigDecimal salaryMinimum,
        BigDecimal salaryMaximum,
        String salaryCurrency,
        String salaryInterval,
        Instant publishedAt,
        Instant sourceUpdatedAt,
        Instant expiresAt) {
}
