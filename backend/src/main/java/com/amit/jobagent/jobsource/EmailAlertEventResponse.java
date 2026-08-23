package com.amit.jobagent.jobsource;
import java.util.List;import java.util.UUID;
public record EmailAlertEventResponse(UUID eventId,String messageId,boolean replayed,EmailIngestionStatus status,int jobCount,int createdCount,int updatedCount,int unchangedCount,int duplicateCount,int failedCount,List<EmailAlertJobResult>results){}
