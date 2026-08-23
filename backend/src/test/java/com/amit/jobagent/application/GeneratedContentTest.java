package com.amit.jobagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneratedContentTest {

    @Test
    void userEditChangesOriginAndInvalidatesAutomaticVerification() {
        UUID revisionId = UUID.fromString("50000000-0000-0000-0000-000000000001");
        var content = new GeneratedContent(
                revisionId,
                GeneratedContentType.PROFESSIONAL_SUMMARY,
                "summary",
                2,
                "Verified generated text",
                ContentOrigin.AI_GENERATED,
                ContentVerificationStatus.VERIFIED);

        content.edit("Candidate-reviewed text");

        assertThat(content.revisionId).isEqualTo(revisionId);
        assertThat(content.type).isEqualTo(GeneratedContentType.PROFESSIONAL_SUMMARY);
        assertThat(content.key).isEqualTo("summary");
        assertThat(content.order).isEqualTo(2);
        assertThat(content.text).isEqualTo("Candidate-reviewed text");
        assertThat(content.origin).isEqualTo(ContentOrigin.USER_EDITED);
        assertThat(content.userEdited).isTrue();
        assertThat(content.verification).isEqualTo(ContentVerificationStatus.UNVERIFIED);
    }
}
