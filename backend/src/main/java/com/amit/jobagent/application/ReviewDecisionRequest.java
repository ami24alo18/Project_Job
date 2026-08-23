package com.amit.jobagent.application;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.*;
public record ReviewDecisionRequest(@Valid@NotNull ReviewChecklist checklist,@Size(max=2000)String comment,@NotNull Long reviewRecordVersion,Map<UUID,QuestionResolution> questionResolutions){}
