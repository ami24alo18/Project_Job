package com.amit.jobagent.application.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.amit.jobagent.application.ArtifactType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RenderedArtifactTest {

    @Test
    void derivesAndProtectsArtifactMetadata() {
        byte[] source = "safe preview".getBytes(StandardCharsets.UTF_8);
        RenderedArtifact artifact = RenderedArtifact.create(
                ArtifactType.HTML_PREVIEW,
                "Candidate_Role_Company_20260822.html",
                "ats-single-column-v1",
                source);

        source[0] = 'X';
        byte[] returned = artifact.bytes();
        returned[0] = 'Y';

        assertThat(new String(artifact.bytes(), StandardCharsets.UTF_8)).isEqualTo("safe preview");
        assertThat(artifact.contentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(artifact.sizeBytes()).isEqualTo(12);
        assertThat(artifact.sha256Checksum()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsInconsistentMetadata() {
        byte[] content = "PDF".getBytes(StandardCharsets.UTF_8);

        assertThatIllegalArgumentException().isThrownBy(() -> new RenderedArtifact(
                ArtifactType.PDF_RESUME,
                "resume.pdf",
                "text/plain",
                content.length,
                "0".repeat(64),
                "v1",
                content));
        assertThatIllegalArgumentException().isThrownBy(() -> RenderedArtifact.create(
                ArtifactType.PDF_RESUME,
                "resume.docx",
                "v1",
                content));
    }
}
