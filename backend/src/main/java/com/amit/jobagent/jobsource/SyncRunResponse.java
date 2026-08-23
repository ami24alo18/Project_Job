package com.amit.jobagent.jobsource;
import java.util.UUID;public record SyncRunResponse(UUID runId,JobSourceRunStatus status){}
