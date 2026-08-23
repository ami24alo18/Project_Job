package com.amit.jobagent.preference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
public record SearchPreferenceRequest(
 @NotEmpty @Size(max=50) Set<String> targetTitles, @Size(max=100) Set<String> preferredLocations,
 @Size(max=100) Set<String> excludedLocations, @Size(max=200) Set<String> requiredSkills,
 @Size(max=200) Set<String> preferredSkills, @Size(max=200) Set<String> excludedCompanies,
 @Size(max=200) Set<String> excludedKeywords, @Min(0) @Max(100) int minimumExperienceYears,
 @Min(0) @Max(100) int maximumExperienceYears, @Min(0) @Max(100) int minimumMatchScore,
 @Min(1) @Max(500) int maximumDailyShortlist, @Min(1) @Max(100) int maximumDailyApplications,
 boolean remoteAllowed,boolean hybridAllowed,boolean onsiteAllowed,Long recordVersion) {
    public SearchPreferenceRequest { targetTitles=safe(targetTitles);preferredLocations=safe(preferredLocations);excludedLocations=safe(excludedLocations);requiredSkills=safe(requiredSkills);preferredSkills=safe(preferredSkills);excludedCompanies=safe(excludedCompanies);excludedKeywords=safe(excludedKeywords); }
    private static Set<String> safe(Set<String> value){return value==null?Set.of():value;}
}
