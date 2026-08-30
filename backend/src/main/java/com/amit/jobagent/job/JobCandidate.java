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
        Instant expiresAt,
        JobIngestionProvider ingestionProvider,
        String originPublisher,
        String discoveryQuery,
        UUID externalEventId,
        String extractionRecipeVersion) {

    public JobCandidate(
            UUID sourceId, JobSourceType sourceType, String externalId, String company, String title,
            String location, String countryCode, WorkplaceType workplaceType, EmploymentType employmentType,
            String department, String team, String description, boolean descriptionHtml, String applyUrl,
            String sourceUrl, BigDecimal salaryMinimum, BigDecimal salaryMaximum, String salaryCurrency,
            SalaryInterval salaryInterval, Instant publishedAt, Instant sourceUpdatedAt, Instant expiresAt) {
        this(sourceId, sourceType, externalId, company, title, location, countryCode, workplaceType,
                employmentType, department, team, description, descriptionHtml, applyUrl, sourceUrl,
                salaryMinimum, salaryMaximum, salaryCurrency, salaryInterval, publishedAt, sourceUpdatedAt,
                expiresAt, JobIngestionProvider.defaultFor(sourceType), null, null, null, null);
    }
}
