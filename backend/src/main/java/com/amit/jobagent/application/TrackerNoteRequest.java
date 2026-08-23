package com.amit.jobagent.application;import jakarta.validation.constraints.*;public record TrackerNoteRequest(@NotBlank@Size(max=1000)String note){}
