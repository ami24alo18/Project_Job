package com.amit.jobagent.jobsource;
import java.time.Instant;import java.util.UUID;
public record JobSourceRunErrorResponse(UUID id,String externalId,String safeErrorCode,String safeErrorMessage,Instant createdAt){}
