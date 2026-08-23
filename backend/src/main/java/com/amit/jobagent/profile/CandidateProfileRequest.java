package com.amit.jobagent.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CandidateProfileRequest(
        @Schema(example="Fictional Candidate") @NotBlank @Size(max=150) String fullName,
        @Schema(example="candidate@example.test") @NotBlank @Email @Size(max=254) String email,
        @Size(max=40) @Pattern(regexp="^$|^[+()0-9 .-]{7,40}$",message="must be a valid international phone number") String phone,
        @Schema(example="Platform Engineer") @NotBlank @Size(max=150) String professionalTitle,
        @Schema(example="Builds reliable fictional distributed systems.") @Size(max=4000) String professionalSummary,
        @Size(max=200) String currentCompany,
        @Size(max=200) String currentLocation,
        @Min(0) int totalExperienceMonths,
        @Min(0) int noticePeriodDays,
        boolean servingNoticePeriod,
        @Size(max=500) @Pattern(regexp="^$|^https?://.+",message="must use http or https") String linkedinUrl,
        @Size(max=500) @Pattern(regexp="^$|^https?://.+",message="must use http or https") String githubUrl,
        @Size(max=500) @Pattern(regexp="^$|^https?://.+",message="must use http or https") String leetcodeUrl,
        @Schema(example="https://portfolio.example.test") @Size(max=500) @Pattern(regexp="^$|^https?://.+",message="must use http or https") String portfolioUrl,
        @Schema(description="Omit for initial creation; required for updates", example="0") Long recordVersion) {}
