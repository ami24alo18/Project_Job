package com.amit.jobagent.resumefact;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
public record ResumeFactResponse(UUID id,UUID profileId,ResumeFactCategory category,String statement,String company,LocalDate startDate,LocalDate endDate,
 Set<String> skillTags,Set<String> domainTags,ResumeFactStatus status,ResumeFactSourceType sourceType,UUID sourceDocumentId,
 String sourceReference,String evidenceText,Instant verifiedAt,String verifiedBy,long recordVersion,Instant createdAt,Instant updatedAt) {}
