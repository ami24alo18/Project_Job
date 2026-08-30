package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.EmploymentType;
import com.amit.jobagent.job.SalaryInterval;
import com.amit.jobagent.job.WorkplaceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record ExternalJobRecordRequest(
        @NotBlank @Size(max = 300) String externalId,
        @Size(max = 80) String originPublisher,
        @Size(max = 200) String company,
        @Size(max = 300) String title,
        @Size(max = 300) String location,
        @Size(max = 2) String countryCode,
        WorkplaceType workplaceType,
        EmploymentType employmentType,
        @Size(max = 200) String department,
        @Size(max = 200) String team,
        @Size(max = 100000) String description,
        @Size(max = 2000) String applyUrl,
        @Size(max = 2000) String sourceUrl,
        BigDecimal salaryMinimum,
        BigDecimal salaryMaximum,
        @Size(max = 3) String salaryCurrency,
        SalaryInterval salaryInterval,
        Instant publishedAt,
        Instant sourceUpdatedAt,
        Instant expiresAt) {}
