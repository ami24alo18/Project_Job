package com.amit.jobagent.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record NormalizedJob(
        UUID sourceId, JobSourceType sourceType, String externalId, String company, String title,
        String location, String countryCode, WorkplaceType workplaceType, EmploymentType employmentType,
        String department, String team, String descriptionPlainText, boolean descriptionTruncated,
        String applyUrl, String canonicalApplyUrl, String sourceUrl, BigDecimal salaryMinimum,
        BigDecimal salaryMaximum, String salaryCurrency, SalaryInterval salaryInterval, Instant publishedAt,
        Instant sourceUpdatedAt, Instant expiresAt, String fingerprint, String contentHash,
        JobPostingStatus initialStatus) {
}
