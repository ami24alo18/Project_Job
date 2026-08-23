package com.amit.jobagent.application.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.amit.jobagent.application.ArtifactType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SafeFilenameSanitizerTest {

    @Test
    void createsPortableResumeFileNameFromUntrustedComponents() {
        String fileName = SafeFilenameSanitizer.resumeFileName(
                " Avery / Example ",
                "Staff: Engineer?",
                "ACME*<>|",
                LocalDate.of(2026, 8, 22),
                ArtifactType.PDF_RESUME);

        assertThat(fileName).isEqualTo("Avery_Example_Staff_Engineer_ACME_20260822.pdf");
        assertThat(fileName).doesNotContain("/", "\\", ":", "*", "?", "<", ">", "|");
    }

    @Test
    void handlesReservedBlankUnicodeAndLongComponents() {
        assertThat(SafeFilenameSanitizer.sanitizeComponent("con")).isEqualTo("_con");
        assertThat(SafeFilenameSanitizer.sanitizeComponent(" .:? ")).isEqualTo("Unknown");
        assertThat(SafeFilenameSanitizer.sanitizeComponent("José 李雷")).isEqualTo("José_李雷");
        assertThat(SafeFilenameSanitizer.sanitizeComponent("a".repeat(100)))
                .hasSize(48)
                .isEqualTo("a".repeat(48));
    }

    @Test
    void choosesOnlyTheKnownExtensionForEachArtifactType() {
        LocalDate date = LocalDate.of(2026, 1, 2);

        assertThat(SafeFilenameSanitizer.resumeFileName("A B", "Role", "Co", date, ArtifactType.HTML_PREVIEW))
                .endsWith("_20260102.html");
        assertThat(SafeFilenameSanitizer.resumeFileName("A B", "Role", "Co", date, ArtifactType.PDF_RESUME))
                .endsWith("_20260102.pdf");
        assertThat(SafeFilenameSanitizer.resumeFileName("A B", "Role", "Co", date, ArtifactType.DOCX_RESUME))
                .endsWith("_20260102.docx");
    }
}
