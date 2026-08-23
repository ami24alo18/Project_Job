package com.amit.jobagent.application;import jakarta.validation.constraints.*;public record GeneratedContentUpdateRequest(@NotBlank@Size(max=12000)String text,@NotNull Long recordVersion){}
