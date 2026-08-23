package com.amit.jobagent.job;

import java.time.Instant;
import java.util.UUID;

public record JobMatchingView(UUID id, String company, String title, String location,
        WorkplaceType workplaceType, EmploymentType employmentType, String description,
        Instant expiresAt, String contentHash, JobPostingStatus status) {}
