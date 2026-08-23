package com.amit.jobagent.matching;import java.time.Instant;import java.util.UUID;
public record JobEvaluationResponse(UUID id,UUID jobId,UUID profileVersionId,UUID ruleEvaluationId,EvaluationStatus status,Recommendation recommendation,Integer overallScore,Integer confidence,String safeErrorCode,String safeErrorMessage,boolean stale,Instant createdAt,Instant completedAt){}
