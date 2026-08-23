package com.amit.jobagent.job;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record JobCandidate(
        UUID sourceId,
        JobSourceType sourceType,
        String externalId,
        String company,
        String title,
        String location,
        String countryCode,
        WorkplaceType workplaceType,
        EmploymentType employmentType,
        String department,
        String team,
        String description,
        boolean descriptionHtml,
        String applyUrl,
        String sourceUrl,
        BigDecimal salaryMinimum,
        BigDecimal salaryMaximum,
        String salaryCurrency,
        SalaryInterval salaryInterval,
        Instant publishedAt,
        Instant sourceUpdatedAt,
        Instant expiresAt) {
}
