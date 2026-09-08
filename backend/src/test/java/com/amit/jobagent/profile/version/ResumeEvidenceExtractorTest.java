package com.amit.jobagent.profile.version;

import static org.assertj.core.api.Assertions.assertThat;

import com.amit.jobagent.document.ActiveResumeEvidence;
import com.amit.jobagent.resumefact.ResumeFactCategory;
import com.amit.jobagent.resumefact.ResumeFactSourceType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResumeEvidenceExtractorTest {
    private final ResumeEvidenceExtractor extractor = new ResumeEvidenceExtractor();

    @Test
    void createsStableSectionAwareEvidenceFromActiveResume() {
        UUID profileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        var resume = new ActiveResumeEvidence(documentId, "checksum", "resume.pdf", """
                Amit Kumar Gupta
                SKILLS
                Java, Spring Boot, AWS
                EXPERIENCE
                Built scalable payment microservices and reduced API latency by 30%.
                PROJECTS
                Created a job application agent using Java and React.
                """, Instant.parse("2026-01-01T00:00:00Z"));

        var first = extractor.extract(profileId, resume);
        var second = extractor.extract(profileId, resume);

        assertThat(first).hasSize(3);
        assertThat(first).extracting(f -> f.id()).containsExactlyElementsOf(second.stream().map(f -> f.id()).toList());
        assertThat(first).allMatch(f -> f.sourceType() == ResumeFactSourceType.RESUME_DOCUMENT
                && f.sourceDocumentId().equals(documentId));
        assertThat(first).anyMatch(f -> f.category() == ResumeFactCategory.SKILL
                && f.skillTags().contains("Spring Boot"));
        assertThat(first).anyMatch(f -> f.category() == ResumeFactCategory.EMPLOYMENT
                && f.statement().contains("latency by 30%"));
        assertThat(first).anyMatch(f -> f.category() == ResumeFactCategory.PROJECT);
    }

    @Test
    void preservesFullResumeEvidenceAndJoinsWrappedBullets() {
        UUID profileId = UUID.randomUUID();
        var resume = new ActiveResumeEvidence(UUID.randomUUID(), "full-resume", "resume.pdf", """
                Candidate Name
                candidate@example.test | LinkedIn

                Profile Summary
                Backend engineer building scalable Java and Spring Boot services,
                with experience in distributed systems and AWS.

                Technologies
                Coding Languages: Java, Python, SQL
                Frameworks: Spring Boot, Hibernate
                Cloud: AWS, Docker

                Experience
                Example Labs November 2025 - Present
                Senior Software Engineer
                • Led development of a repayment platform supporting 5M transactions/day,

                enabling real-time processing with Java and Spring Boot.
                • Reduced API response times by 30% through query optimization.

                Previous Company June 2022 - October 2025
                Software Engineer
                • Integrated more than 10 external APIs using Java.

                Projects
                Application Agent
                • Built an automated workflow using Spring Boot and Docker.
                """, Instant.parse("2026-01-01T00:00:00Z"));

        var facts = extractor.extract(profileId, resume);

        assertThat(facts).hasSizeGreaterThanOrEqualTo(12);
        assertThat(facts).noneMatch(f -> f.statement().contains("candidate@example.test"));
        assertThat(facts).anyMatch(f -> f.statement().contains("distributed systems and AWS"));
        assertThat(facts).anyMatch(f -> f.statement().contains("enabling real-time processing")
                && "Example Labs".equals(f.company())
                && f.skillTags().contains("Spring Boot"));
        assertThat(facts).anyMatch(f -> f.statement().contains("10 external APIs")
                && "Previous Company".equals(f.company()));
    }
}
