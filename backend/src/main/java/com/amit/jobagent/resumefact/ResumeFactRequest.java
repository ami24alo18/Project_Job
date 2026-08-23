package com.amit.jobagent.resumefact;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
public record ResumeFactRequest(@Schema(example="PROJECT") @NotNull ResumeFactCategory category,@Schema(example="Built a fictional Java service used by an example team.") @NotBlank @Size(max=4000) String statement,
 @Size(max=200) String company,LocalDate startDate,LocalDate endDate,@Size(max=100) Set<String> skillTags,
 @Size(max=100) Set<String> domainTags,@Schema(example="Fictional project record") @Size(max=500) String sourceReference,@Schema(example="Reviewed against an example portfolio artifact.") @Size(max=4000) String evidenceText,Long recordVersion){
 public ResumeFactRequest{skillTags=skillTags==null?Set.of():ResumeFactService.normalize(skillTags);domainTags=domainTags==null?Set.of():ResumeFactService.normalize(domainTags);}
}
