package com.amit.jobagent.matching;
import java.time.Instant;import java.util.UUID;
public record CompletedEvaluationSnapshot(UUID id,UUID jobId,UUID profileVersionId,EvaluationStatus status,
 Recommendation recommendation,Integer overallScore,boolean stale,String jobChecksum,String profileChecksum,
 String structuredOutput,String summary,Instant completedAt,String checksum){}
