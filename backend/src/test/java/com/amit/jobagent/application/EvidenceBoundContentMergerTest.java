package com.amit.jobagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceBoundContentMergerTest {
    private final GeneratedContentValidator validator = new GeneratedContentValidator();
    private final EvidenceBoundContentMerger merger = new EvidenceBoundContentMerger(validator);

    @Test
    void keepsSupportedAiSectionsAndFallsBackOnlyForRejectedSections() {
        UUID factId = UUID.randomUUID();
        var fact = new VerifiedFactSnapshot(
                factId, "EMPLOYMENT", "Developed Java Spring Boot backend services.", "Example Labs",
                null, null, Set.of("Java", "Spring Boot"), Set.of("Backend"));
        var request = new ApplicationContentGenerationRequest(
                UUID.randomUUID(), "profile", "job", "evaluation", "Backend Engineer", "Example Corp",
                "Backend Engineer using Java and Kubernetes", List.of(fact), List.of(), List.of());
        var plan = new TailoringPlan(
                List.of(factId), List.of(), List.of("Java", "Spring Boot"), List.of("Kubernetes"), List.of(), "concise");
        var generated = new GeneratedApplicationContent(
                List.of(
                        new GeneratedContentItem(GeneratedContentType.PROFESSIONAL_SUMMARY, "summary", 0,
                                "Experienced in developing scalable Java Spring Boot backend services."),
                        new GeneratedContentItem(GeneratedContentType.EXPERIENCE_BULLET, "experience-1", 1,
                                "Developed scalable Java Spring Boot backend services."),
                        new GeneratedContentItem(GeneratedContentType.SKILL_SECTION, "skills", 2,
                                "Java, Kubernetes")),
                List.of(
                        candidate("summary", "Experienced in developing scalable Java Spring Boot backend services.", factId),
                        candidate("experience-1", "Developed scalable Java Spring Boot backend services.", factId),
                        candidate("skills", "Java, Kubernetes", factId)),
                List.of(), List.of("Kubernetes"));
        var jobEvidence = new JobClaimEvidence(Map.of(), Map.of(
                "job.title", "Backend Engineer", "job.company", "Example Corp",
                "job.description", request.jobDescription()));

        GeneratedApplicationContent result = merger.merge(generated, request, plan, jobEvidence);

        assertThat(result.contents()).anySatisfy(item -> {
            assertThat(item.key()).isEqualTo("summary");
            assertThat(item.text()).contains("scalable Java Spring Boot");
        });
        assertThat(result.contents()).anySatisfy(item -> {
            assertThat(item.key()).isEqualTo("experience-1");
            assertThat(item.text()).contains("scalable Java Spring Boot");
        });
        assertThat(result.contents()).noneMatch(item -> item.text().contains("Kubernetes"));
        assertThat(result.contents()).anyMatch(item -> item.key().equals("safe-skills"));
        assertThat(result.warnings()).contains(
                EvidenceBoundContentMerger.REBOUND_WARNING,
                EvidenceBoundContentMerger.PARTIAL_FALLBACK_WARNING);
        assertThat(result.warnings()).doesNotContain("MODEL_GROUNDED_FALLBACK");
        assertThat(validator.validate(result, List.of(fact), jobEvidence).valid()).isTrue();
    }

    private static GeneratedClaimAtom candidate(String key, String text, UUID factId) {
        return new GeneratedClaimAtom(key, text, ClaimType.CANDIDATE_FACT, List.of(factId), List.of(), null);
    }
}
