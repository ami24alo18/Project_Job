package com.amit.jobagent.job;

import java.util.UUID;

public record JobIngestionOutcome(UUID jobId, JobIngestionAction action) {}
