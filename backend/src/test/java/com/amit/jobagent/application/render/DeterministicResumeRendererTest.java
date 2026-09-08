package com.amit.jobagent.application.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.amit.jobagent.application.ArtifactType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class DeterministicResumeRendererTest {
    private final DeterministicResumeRenderer renderer = new DeterministicResumeRenderer();

    @Test
    void rendersReadableAtsArtifactsWithVerifiedMetadataAndChecksums() throws Exception {
        ResumeDocumentModel model = sampleModel();

        List<RenderedArtifact> rendered = renderer.renderAll(model);
        var artifacts = new EnumMap<ArtifactType, RenderedArtifact>(ArtifactType.class);
        rendered.forEach(artifact -> artifacts.put(artifact.artifactType(), artifact));

        assertThat(rendered).extracting(RenderedArtifact::artifactType).containsExactly(
                ArtifactType.HTML_PREVIEW,
                ArtifactType.PDF_RESUME,
                ArtifactType.DOCX_RESUME);
        assertThat(artifacts.get(ArtifactType.HTML_PREVIEW).contentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(artifacts.get(ArtifactType.PDF_RESUME).contentType()).isEqualTo("application/pdf");
        assertThat(artifacts.get(ArtifactType.DOCX_RESUME).contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        for (RenderedArtifact artifact : rendered) {
            assertThat(artifact.fileName()).startsWith("Avery_Example_Platform_Engineer_Fictional_Labs_20260822");
            assertThat(artifact.templateVersion()).isEqualTo(DeterministicResumeRenderer.TEMPLATE_VERSION);
            assertThat(artifact.sizeBytes()).isEqualTo(artifact.bytes().length).isPositive();
            assertThat(artifact.sha256Checksum()).isEqualTo(sha256(artifact.bytes()));
        }

        String html = new String(artifacts.get(ArtifactType.HTML_PREVIEW).bytes(), StandardCharsets.UTF_8);
        String htmlText = Jsoup.parse(html).text();
        assertExpectedText(htmlText);
        assertThat(htmlText).doesNotContain("Target:", "Company:");

        byte[] pdfBytes = artifacts.get(ArtifactType.PDF_RESUME).bytes();
        assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var pdf = Loader.loadPDF(pdfBytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            assertExpectedText(new PDFTextStripper().getText(pdf));
        }

        byte[] docxBytes = artifacts.get(ArtifactType.DOCX_RESUME).bytes();
        try (var document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            String docxText = String.join("\n", document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .toList());
            assertExpectedText(docxText);
        }
    }

    @Test
    void escapesAllModelTextAndEmitsNoExternalOrExecutableResources() throws Exception {
        String first = renderer.renderHtmlSource(sampleModel());
        String second = renderer.renderHtmlSource(sampleModel());
        String lower = first.toLowerCase(Locale.ROOT);

        assertThat(second).isEqualTo(first);
        assertThat(first).contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; Reliability");
        assertThat(lower)
                .doesNotContain("<script", "<img", "<iframe", "<object", "<table", " href=", " src=", "url(", "@import");

        RenderedArtifact docx = renderer.render(sampleModel(), ArtifactType.DOCX_RESUME);
        String packageXml = unzipText(docx.bytes());
        assertThat(packageXml).doesNotContain("TargetMode=\"External\"", "vbaProject.bin", "<w:tbl");
    }

    @Test
    void rendersByteStableArtifactsForTheSameCanonicalModel() {
        List<RenderedArtifact> first = renderer.renderAll(sampleModel());
        List<RenderedArtifact> second = renderer.renderAll(sampleModel());

        assertThat(second).hasSameSizeAs(first);
        assertSoftly(softly -> {
            for (int index = 0; index < first.size(); index++) {
                ArtifactType type = first.get(index).artifactType();
                softly.assertThat(second.get(index).artifactType()).isEqualTo(type);
                softly.assertThat(second.get(index).fileName()).isEqualTo(first.get(index).fileName());
                softly.assertThat(second.get(index).sha256Checksum())
                        .as("%s checksum", type)
                        .isEqualTo(first.get(index).sha256Checksum());
                softly.assertThat(second.get(index).bytes())
                        .as("%s bytes", type)
                        .containsExactly(first.get(index).bytes());
            }
        });
    }

    @Test
    void keepsAFullLengthMasterResumeOnOneLetterPage() throws Exception {
        ResumeDocumentModel base = sampleModel();
        String bullet = "Designed and delivered scalable backend microservices, improving response times by 30% while strengthening reliability, maintainability, and operational visibility.";
        var experience = List.of(
                new ResumeEntry("Software Engineer", "Nouveau Labs", null, "November 2025 - Present", List.of(bullet, bullet, bullet)),
                new ResumeEntry("Senior Software Engineer", "ADITYA BIRLA CAPITAL", null, "February 2024 - November 2025", List.of(bullet, bullet, bullet)),
                new ResumeEntry("Software Engineer", "ZestMoney", null, "June 2022 - February 2024", List.of(bullet, bullet, bullet)));
        var model = new ResumeDocumentModel(base.contact(), base.targetRole(), base.targetCompany(), null,
                List.of("Backend Developer with 4+ years of experience building scalable, high-performance systems using Java, Spring Boot, Python, and AWS. Proficient in microservices, distributed systems, cloud-native applications, AI-powered solutions, workflow automation, and intelligent APIs."),
                List.of(
                        "Coding Languages: Java, C++, Python, SQL, HTML",
                        "AI & Automation: LLMs, AI Agents, Prompt Engineering, Workflow Automation, n8n, AI API Integration",
                        "DB & Frameworks: MySQL, PostgreSQL, Redis, Spring, Spring Boot, Hibernate",
                        "Tools/Concepts: RabbitMQ, Kafka, Git, GitLab, AWS, Data Structures & Algorithms, DBMS, Microservices"),
                experience, base.projects(), base.education(), base.artifactDate());

        byte[] pdfBytes = renderer.render(model, ArtifactType.PDF_RESUME).bytes();
        Files.write(Path.of("target", "master-resume-preview.pdf"), pdfBytes);
        try (var pdf = Loader.loadPDF(pdfBytes)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
        }
    }

    private static void assertExpectedText(String text) {
        assertThat(text)
                .contains("Avery Example")
                .contains("Senior Software Engineer")
                .contains("Fictional Labs")
                .contains("Java 21")
                .contains("PostgreSQL")
                .contains("Reduced deployment time by 30%")
                .contains("Verified Reliability Console")
                .contains("Fictional Institute of Technology");
    }

    private static ResumeDocumentModel sampleModel() {
        var contact = new ResumeContact(
                "Avery Example",
                "avery@example.test",
                "000-000-0000",
                "Bengaluru, India",
                List.of("https://linkedin.example.test/avery", "https://github.example.test/avery"));
        var experience = new ResumeEntry(
                "Senior Software Engineer",
                "Fictional Labs",
                "Bengaluru, India",
                "January 2023 - Present",
                List.of(
                        "Reduced deployment time by 30% using verified automation.",
                        "Built reliable Spring Boot services backed by PostgreSQL."));
        var project = new ResumeEntry(
                "Verified Reliability Console",
                null,
                null,
                "2024",
                List.of("Created a Java 21 service with traceable operational evidence."));
        var education = new ResumeEntry(
                "Bachelor of Technology in Computer Science",
                "Fictional Institute of Technology",
                "Pune, India",
                "2018 - 2022",
                List.of());
        return new ResumeDocumentModel(
                contact,
                "Platform Engineer",
                "Fictional Labs",
                "Platform Engineer <script>alert(\"x\")</script> & Reliability",
                List.of("Builds secure, observable systems <script>alert(\"x\")</script> & Reliability."),
                List.of("Java 21", "Spring Boot", "PostgreSQL"),
                List.of(experience),
                List.of(project),
                List.of(education),
                LocalDate.of(2026, 8, 22));
    }

    private static String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String unzipText(byte[] archive) throws Exception {
        var text = new StringBuilder();
        try (var input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                text.append(entry.getName()).append('\n');
                var entryBytes = new ByteArrayOutputStream();
                input.transferTo(entryBytes);
                text.append(entryBytes.toString(StandardCharsets.UTF_8)).append('\n');
            }
        }
        return text.toString();
    }
}
