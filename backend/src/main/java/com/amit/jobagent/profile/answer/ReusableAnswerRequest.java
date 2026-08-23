package com.amit.jobagent.profile.answer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record ReusableAnswerRequest(@Schema(example="Are you authorized to work in Exampleland?") @NotBlank @Size(max=1000)String question,@Schema(example="Yes; manual review is still required.") @NotBlank @Size(max=10000)String answer,
 @NotNull ReusableAnswerCategory category,AnswerSensitivity sensitivity,Long recordVersion){}
