package com.amit.jobagent.matching;
import java.time.Instant; import java.util.UUID;
public record MatchingConfigurationResponse(UUID id,UUID profileId,int skillsWeight,int experienceWeight,int roleWeight,int locationWeight,int domainWeight,int compensationWeight,int strongApplyThreshold,int applyThreshold,int manualReviewThreshold,int maximumAllowedExperienceGap,int maximumJobsPerBatch,int maximumDailyAiRequests,long maximumDailyInputTokens,String rulesetVersion,long recordVersion,Instant createdAt,Instant updatedAt){}
