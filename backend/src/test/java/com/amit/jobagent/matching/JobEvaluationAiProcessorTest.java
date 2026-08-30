package com.amit.jobagent.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amit.jobagent.ai.*;
import com.amit.jobagent.job.*;
import com.amit.jobagent.profile.version.PublishedProfileSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class JobEvaluationAiProcessorTest {
    @Test
    void completesAValidatedEvaluationWithoutSendingCandidateContactData() throws Exception {
        UUID factId = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper();
        var snapshot = mapper.readTree("""
                {"profile":{"professionalTitle":"Backend Engineer","currentCompany":"Example Labs",
                "currentLocation":"Delhi","totalExperienceMonths":48,"email":"private@example.test","phone":"123"},
                "preferences":{"targetTitles":["Backend Engineer"],"preferredLocations":["Delhi"]},
                "verifiedResumeFacts":[{"id":"%s","category":"EMPLOYMENT","statement":"Built Java services.",
                "company":"Example Labs","skillTags":["Java"],"domainTags":["Backend"],
                "evidenceText":"private raw evidence"}]}
                """.formatted(factId));
        var profile = new PublishedProfileSnapshot(UUID.randomUUID(), UUID.randomUUID(), "p".repeat(64), snapshot);
        var job = new JobMatchingView(UUID.randomUUID(), "Example Company", "Backend Engineer", "Delhi",
                WorkplaceType.HYBRID, EmploymentType.FULL_TIME, "Requires Java and Spring Boot.", null,
                "j".repeat(64), JobPostingStatus.READY_FOR_EVALUATION);
        var output = new LlmJobEvaluationResponse();
        output.status = "EVALUATED"; output.confidence = 90; output.summary = "Strong verified match.";
        output.scores = new LlmJobEvaluationResponse.Scores();
        output.scores.skills = 90; output.scores.experience = 85; output.scores.role = 90;
        output.scores.location = 90; output.scores.domain = 85; output.scores.compensation = null;
        var requirement = new LlmJobEvaluationResponse.Requirement();
        requirement.requirementText = "Java"; requirement.requirementType = "REQUIRED";
        requirement.category = "SKILL"; requirement.matchStatus = "MATCHED";
        requirement.candidateFactIds = List.of(factId.toString()); requirement.jobEvidence = "Requires Java";
        output.requirements = List.of(requirement); output.matchedSkills = List.of("Java");
        output.missingRequiredSkills = List.of(); output.missingPreferredSkills = List.of();
        output.risks = List.of(); output.questionsNeedingUserInput = List.of();

        JobEvaluationLlmClient client = mock(JobEvaluationLlmClient.class);
        var request = ArgumentCaptor.forClass(LlmJobEvaluationRequest.class);
        when(client.evaluate(request.capture())).thenReturn(new LlmJobEvaluationResult(
                output, "response-1", 100L, 50L, 150L, 20L));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        var config = new AiEvaluationProperties(true, "unused", "gpt-5.6-terra", "medium", 30, 2,
                100, 500000, 50000, 25);
        var processor = new JobEvaluationAiProcessor(Optional.of(client), mapper, jdbc, config, new PromptCatalog());
        var evaluation = new JobEvaluation(job.id(), profile.id(), UUID.randomUUID(), job.contentHash(),
                profile.checksum(), "c".repeat(64), "q".repeat(64), "s".repeat(64), "k".repeat(64));
        var matching = new MatchingConfigurationResponse(UUID.randomUUID(), profile.profileId(), 35, 20, 15,
                15, 10, 5, 85, 75, 60, 1, 25, 100, 500000, "v1", 0, Instant.now(), Instant.now());

        processor.process(evaluation, job, profile, matching);

        assertThat(evaluation.status).isEqualTo(EvaluationStatus.SUCCEEDED);
        assertThat(evaluation.recommendation).isEqualTo(Recommendation.STRONG_APPLY);
        assertThat(evaluation.overallScore).isGreaterThanOrEqualTo(85);
        assertThat(request.getValue().serializedInput()).contains("Built Java services.", factId.toString());
        assertThat(request.getValue().serializedInput()).doesNotContain("private@example.test", "123",
                "private raw evidence");
    }
}
