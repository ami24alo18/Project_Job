package com.amit.jobagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.amit.jobagent.job.JobMatchingView;
import com.amit.jobagent.job.JobPostingStatus;
import com.amit.jobagent.matching.EvaluationRequirementProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResumeMatchScorerTest {
    @Test
    void comparesBothDocumentsAgainstOneRequirementSetWithoutAnLlm() {
        UUID revisionId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        var job = new JobMatchingView(UUID.randomUUID(), "Example", "Backend Engineer", "Remote",
                null, null, "Java Spring SQL Kafka", Instant.now().plusSeconds(3600), "hash",
                JobPostingStatus.READY_FOR_EVALUATION);
        var requirements = List.of(
                requirement("Java Spring backend development"),
                requirement("SQL database design"),
                requirement("Kafka event streaming"));
        var facts = List.of(new VerifiedFactSnapshot(
                factId, "EMPLOYMENT", "Developed Java Spring backend services.", "Example Labs",
                null, null, Set.of("Java", "Spring"), Set.of("Backend")));
        var generated = List.of(
                new GeneratedContent(revisionId, GeneratedContentType.PROFESSIONAL_SUMMARY, "summary", 0,
                        "Java Spring backend engineer with SQL database experience.",
                        ContentOrigin.AI_GENERATED, ContentVerificationStatus.VERIFIED));

        ResumeMatchComparison comparison = new ResumeMatchScorer((jd, current, draft) -> Optional.empty())
                .compare(job, requirements, facts, generated);

        assertThat(comparison.generatedDraftScore()).isGreaterThan(comparison.currentResumeScore());
        assertThat(comparison.scoreDelta()).isPositive();
        assertThat(comparison.matchedKeywords()).contains("sql");
        assertThat(comparison.missingKeywords()).contains("kafka");
        assertThat(comparison.method()).isEqualTo(ResumeMatchScorer.FALLBACK_METHOD);
    }

    @Test
    void usesTheSemanticProviderScoresForBothDocuments() {
        var job = new JobMatchingView(UUID.randomUUID(), "Example", "Backend Engineer", "Remote",
                null, null, "Java services", Instant.now().plusSeconds(3600), "hash",
                JobPostingStatus.READY_FOR_EVALUATION);
        SemanticResumeMatchProvider provider = (jd, current, draft) -> Optional.of(
                new SemanticResumeMatchProvider.Scores(61, 78, "NBK_ATS_SEMANTIC_V1_EN_COSINE"));

        ResumeMatchComparison comparison = new ResumeMatchScorer(provider).compare(
                job, List.of(), List.of(new VerifiedFactSnapshot(UUID.randomUUID(), "SKILL", "Java",
                        null, null, null, Set.of("Java"), Set.of())), List.of());

        assertThat(comparison.currentResumeScore()).isEqualTo(61);
        assertThat(comparison.generatedDraftScore()).isEqualTo(78);
        assertThat(comparison.scoreDelta()).isEqualTo(17);
        assertThat(comparison.method()).isEqualTo("NBK_ATS_SEMANTIC_V1_EN_COSINE");
    }

    private static EvaluationRequirementProvider.RequirementView requirement(String text) {
        return new EvaluationRequirementProvider.RequirementView(
                UUID.randomUUID(), text, "REQUIRED", "TECHNICAL", "UNKNOWN", text);
    }
}
