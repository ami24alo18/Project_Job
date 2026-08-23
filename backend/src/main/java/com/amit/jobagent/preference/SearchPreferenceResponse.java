package com.amit.jobagent.preference;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
public record SearchPreferenceResponse(UUID profileId,Set<String> targetTitles,Set<String> preferredLocations,Set<String> excludedLocations,
 Set<String> requiredSkills,Set<String> preferredSkills,Set<String> excludedCompanies,Set<String> excludedKeywords,
 int minimumExperienceYears,int maximumExperienceYears,int minimumMatchScore,int maximumDailyShortlist,int maximumDailyApplications,
 boolean remoteAllowed,boolean hybridAllowed,boolean onsiteAllowed,long recordVersion,Instant createdAt,Instant updatedAt) {}
