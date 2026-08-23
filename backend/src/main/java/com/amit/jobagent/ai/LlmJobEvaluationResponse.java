package com.amit.jobagent.ai;
import java.util.List;
public class LlmJobEvaluationResponse{
 public String status;public int confidence;public Scores scores;public ExperienceRequirement experienceRequirement;public List<Requirement>requirements;public List<String>matchedSkills;public List<String>missingRequiredSkills;public List<String>missingPreferredSkills;public List<String>risks;public List<String>questionsNeedingUserInput;public String summary;
 public static class Scores{public Integer skills;public Integer experience;public Integer role;public Integer location;public Integer domain;public Integer compensation;}
 public static class ExperienceRequirement{public Integer minimumYears;public Integer maximumYears;public String evidence;}
 public static class Requirement{public String requirementText;public String requirementType;public String category;public String matchStatus;public List<String>candidateFactIds;public String jobEvidence;}
}
