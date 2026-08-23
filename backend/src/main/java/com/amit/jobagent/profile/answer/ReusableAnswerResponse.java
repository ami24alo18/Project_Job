package com.amit.jobagent.profile.answer;
import java.time.Instant;
import java.util.UUID;
public record ReusableAnswerResponse(UUID id,UUID profileId,String question,String normalizedQuestion,String answer,ReusableAnswerCategory category,
 AnswerSensitivity sensitivity,ReusableAnswerStatus status,Instant verifiedAt,long recordVersion,Instant createdAt,Instant updatedAt){}
