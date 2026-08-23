package com.amit.jobagent.profile;
import java.time.Instant;
import java.util.UUID;
public record CandidateProfileResponse(UUID id,String fullName,String email,String phone,String professionalTitle,
        String professionalSummary,String currentCompany,String currentLocation,int totalExperienceMonths,int noticePeriodDays,
        boolean servingNoticePeriod,String linkedinUrl,String githubUrl,String leetcodeUrl,String portfolioUrl,
        long recordVersion,Instant createdAt,Instant updatedAt) {}
