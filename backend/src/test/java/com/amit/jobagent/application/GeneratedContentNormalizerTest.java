package com.amit.jobagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneratedContentNormalizerTest {
    @Test
    void repairsDuplicateAndUnknownKeysWithoutChangingProse() {
        UUID factId = UUID.randomUUID();
        var output = new GeneratedApplicationContent(
                List.of(
                        new GeneratedContentItem(GeneratedContentType.EXPERIENCE_BULLET, "experience", 1, "Built Java services."),
                        new GeneratedContentItem(GeneratedContentType.EXPERIENCE_BULLET, "experience", 1, "Improved Spring systems."),
                        new GeneratedContentItem(GeneratedContentType.RECRUITER_MESSAGE, "orphan", 2, "Unsupported orphan.")),
                List.of(
                        candidate("experience", "Built Java services", factId),
                        candidate("experience", "Built Java services", factId),
                        candidate("wrong-key", "Improved Spring systems", factId)),
                List.of(), List.of());

        GeneratedApplicationContent normalized = GeneratedContentNormalizer.normalize(output);

        assertThat(normalized.contents()).extracting(GeneratedContentItem::key)
                .containsExactly("experience", "experience-2");
        assertThat(normalized.contents()).extracting(GeneratedContentItem::text)
                .containsExactly("Built Java services.", "Improved Spring systems.");
        assertThat(normalized.contents()).extracting(GeneratedContentItem::order).doesNotHaveDuplicates();
        assertThat(normalized.claims()).extracting(GeneratedClaimAtom::contentKey)
                .containsExactly("experience", "experience-2");
        assertThat(normalized.warnings()).contains(GeneratedContentNormalizer.WARNING);
    }

    @Test
    void leavesAlreadyConsistentOutputUnchanged() {
        UUID factId = UUID.randomUUID();
        var output = new GeneratedApplicationContent(
                List.of(new GeneratedContentItem(GeneratedContentType.EXPERIENCE_BULLET, "experience-1", 1, "Built Java services.")),
                List.of(candidate("experience-1", "Built Java services", factId)),
                List.of(), List.of());

        assertThat(GeneratedContentNormalizer.normalize(output)).isEqualTo(output);
    }

    private static GeneratedClaimAtom candidate(String key, String text, UUID factId) {
        return new GeneratedClaimAtom(key, text, ClaimType.CANDIDATE_FACT, List.of(factId), List.of(), null);
    }
}
