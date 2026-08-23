package com.amit.jobagent.application;import jakarta.validation.constraints.*;import java.util.*;public record QuestionsRequest(@NotEmpty@Size(max=50)List<@NotBlank@Size(max=2000)String>questions){}
