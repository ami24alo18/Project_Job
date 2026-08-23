package com.amit.jobagent.application.render;

import static com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI;
import static com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI;

import com.amit.jobagent.application.ArtifactType;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;

/** Renders one canonical resume model into deterministic, single-column ATS formats. */
@Component
public final class DeterministicResumeRenderer {
    public static final String TEMPLATE_VERSION = "ats-single-column-v1";

    private static final int HALF_INCH_TWIPS = 720;
    private static final BigInteger A4_WIDTH_TWIPS = BigInteger.valueOf(11_906);
    private static final BigInteger A4_HEIGHT_TWIPS = BigInteger.valueOf(16_838);
    private static final String PDF_PRODUCER = "Job Application Agent ATS Renderer";

    public List<RenderedArtifact> renderAll(ResumeDocumentModel model) {
        Objects.requireNonNull(model, "model is required");
        String html = renderHtmlSource(model);
        var rendered = List.of(
                artifact(model, ArtifactType.HTML_PREVIEW, html.getBytes(StandardCharsets.UTF_8)),
                artifact(model, ArtifactType.PDF_RESUME, renderPdf(html, model.artifactDate())),
                artifact(model, ArtifactType.DOCX_RESUME, renderDocx(model)));
        rendered.forEach(artifact -> validateArtifact(artifact, model));
        return rendered;
    }

    public RenderedArtifact render(ResumeDocumentModel model, ArtifactType artifactType) {
        Objects.requireNonNull(model, "model is required");
        Objects.requireNonNull(artifactType, "artifactType is required");
        var rendered = switch (artifactType) {
            case HTML_PREVIEW -> {
                String html = renderHtmlSource(model);
                yield artifact(model, artifactType, html.getBytes(StandardCharsets.UTF_8));
            }
            case PDF_RESUME -> artifact(
                    model,
                    artifactType,
                    renderPdf(renderHtmlSource(model), model.artifactDate()));
            case DOCX_RESUME -> artifact(model, artifactType, renderDocx(model));
        };
        validateArtifact(rendered, model);
        return rendered;
    }

    private static void validateArtifact(RenderedArtifact artifact, ResumeDocumentModel model) {
        try {
            String expectedName = model.contact().fullName();
            switch (artifact.artifactType()) {
                case HTML_PREVIEW -> {
                    String html = new String(artifact.bytes(), StandardCharsets.UTF_8);
                    String lower = html.toLowerCase(java.util.Locale.ROOT);
                    if (!html.contains(expectedName)
                            || lower.contains("<script")
                            || Pattern.compile("(?iu)(?:src|href)\\s*=|url\\s*\\(").matcher(html).find()) {
                        throw new DocumentRenderingException("HTML preview failed safety or expected-text validation");
                    }
                }
                case PDF_RESUME -> {
                    try (var document = Loader.loadPDF(artifact.bytes())) {
                        String text = new PDFTextStripper().getText(document);
                        if (document.getNumberOfPages() == 0 || !text.contains(expectedName)) {
                            throw new DocumentRenderingException("PDF resume failed readability or expected-text validation");
                        }
                    }
                }
                case DOCX_RESUME -> {
                    if (hasForbiddenDocxPart(artifact.bytes())) {
                        throw new DocumentRenderingException("DOCX resume contains an executable or external resource");
                    }
                    try (var document = new XWPFDocument(new ByteArrayInputStream(artifact.bytes()))) {
                        String text = document.getParagraphs().stream()
                                .map(XWPFParagraph::getText)
                                .collect(java.util.stream.Collectors.joining("\n"));
                        if (!text.contains(expectedName)) {
                            throw new DocumentRenderingException("DOCX resume failed readability or expected-text validation");
                        }
                    }
                }
            }
        } catch (DocumentRenderingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DocumentRenderingException("Rendered resume artifact failed validation", exception);
        }
    }

    private static boolean hasForbiddenDocxPart(byte[] bytes) throws Exception {
        try (var input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                String name = entry.getName().toLowerCase(java.util.Locale.ROOT);
                if (name.endsWith("vbaproject.bin") || name.contains("oleobject") || name.contains("embeddings/")) {
                    return true;
                }
                if (name.endsWith(".rels")) {
                    String relationships = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    if (relationships.toLowerCase(java.util.Locale.ROOT).contains("targetmode=\"external\"")) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** Returns the canonical XHTML used both for preview and PDF layout. */
    public String renderHtmlSource(ResumeDocumentModel model) {
        Objects.requireNonNull(model, "model is required");
        var html = new StringBuilder(8_192);
        html.append("""
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <title>""");
        appendEscaped(html, model.contact().fullName() + " Resume");
        html.append("""
                </title>
                  <style>
                    @page { size: A4; margin: 0.55in 0.65in; }
                    html, body { margin: 0; padding: 0; }
                    body { color: #111111; font-family: Arial, Helvetica, sans-serif; font-size: 10pt; line-height: 1.28; }
                    main { display: block; width: 100%; }
                    h1 { font-size: 19pt; line-height: 1.1; margin: 0 0 3pt 0; font-weight: bold; }
                    h2 { font-size: 11.5pt; margin: 10pt 0 4pt 0; padding: 0 0 2pt 0; border-bottom: 0.7pt solid #333333; text-transform: uppercase; }
                    h3 { font-size: 10.5pt; margin: 6pt 0 1pt 0; font-weight: bold; }
                    p { margin: 0 0 4pt 0; }
                    ul { margin: 2pt 0 4pt 16pt; padding: 0; }
                    li { margin: 0 0 2pt 0; padding: 0; }
                    .contact, .target, .meta { font-size: 9pt; }
                    .headline { font-size: 11pt; font-weight: bold; margin-top: 5pt; }
                    .skills { margin-bottom: 3pt; }
                    .entry { display: block; page-break-inside: avoid; }
                  </style>
                </head>
                <body>
                <main>
                """);
        html.append("<header><h1>");
        appendEscaped(html, model.contact().fullName());
        html.append("</h1>");
        appendContactHtml(html, model.contact());
        html.append("<p class=\"target\">Target: ");
        appendEscaped(html, model.targetRole());
        html.append(" | Company: ");
        appendEscaped(html, model.targetCompany());
        html.append("</p>");
        if (model.headline() != null) {
            html.append("<p class=\"headline\">");
            appendEscaped(html, model.headline());
            html.append("</p>");
        }
        html.append("</header>");

        appendTextSection(html, "Professional Summary", model.summaryParagraphs());
        appendSkills(html, model.skills());
        appendEntrySection(html, "Experience", model.experience());
        appendEntrySection(html, "Projects", model.projects());
        appendEntrySection(html, "Education", model.education());
        html.append("</main></body></html>");
        return html.toString();
    }

    private static RenderedArtifact artifact(
            ResumeDocumentModel model,
            ArtifactType artifactType,
            byte[] bytes) {
        String fileName = SafeFilenameSanitizer.resumeFileName(
                model.contact().fullName(),
                model.targetRole(),
                model.targetCompany(),
                model.artifactDate(),
                artifactType);
        return RenderedArtifact.create(artifactType, fileName, TEMPLATE_VERSION, bytes);
    }

    private static byte[] renderPdf(String html, LocalDate artifactDate) {
        try (var output = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(output);
            builder.useFastMode();
            builder.withProducer(PDF_PRODUCER);
            builder.useExternalResourceAccessControl((uri, type) -> false, RUN_BEFORE_RESOLVING_URI);
            builder.useExternalResourceAccessControl((uri, type) -> false, RUN_AFTER_RESOLVING_URI);
            builder.run();
            return canonicalizePdf(output.toByteArray(), html, artifactDate);
        } catch (Exception exception) {
            throw new DocumentRenderingException("Unable to render PDF resume", exception);
        }
    }

    private static byte[] canonicalizePdf(byte[] source, String html, LocalDate artifactDate) throws Exception {
        try (var document = Loader.loadPDF(source); var output = new ByteArrayOutputStream()) {
            var timestamp = java.util.GregorianCalendar.from(
                    artifactDate.atStartOfDay(ZoneOffset.UTC));
            var information = document.getDocumentInformation();
            information.setCreationDate(timestamp);
            information.setModificationDate(timestamp);
            information.setProducer(PDF_PRODUCER);

            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(html.getBytes(StandardCharsets.UTF_8));
            var firstId = new COSString(digest);
            firstId.setForceHexForm(true);
            var secondId = new COSString(digest);
            secondId.setForceHexForm(true);
            var identifiers = new COSArray();
            identifiers.add(firstId);
            identifiers.add(secondId);
            document.getDocument().setDocumentID(identifiers);
            document.setDocumentId(artifactDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            document.save(output, CompressParameters.NO_COMPRESSION);
            return output.toByteArray();
        }
    }

    private static byte[] renderDocx(ResumeDocumentModel model) {
        try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            configureDocument(document, model);
            addName(document, model.contact().fullName());
            addContact(document, model.contact());
            addBodyParagraph(document, "Target: " + model.targetRole() + " | Company: " + model.targetCompany(), 9, false);
            if (model.headline() != null) {
                addBodyParagraph(document, model.headline(), 11, true);
            }
            addTextSection(document, "PROFESSIONAL SUMMARY", model.summaryParagraphs());
            addSkills(document, model.skills());
            addEntrySection(document, "EXPERIENCE", model.experience());
            addEntrySection(document, "PROJECTS", model.projects());
            addEntrySection(document, "EDUCATION", model.education());
            document.write(output);
            return canonicalizeDocxPackage(output.toByteArray(), model.artifactDate());
        } catch (Exception exception) {
            throw new DocumentRenderingException("Unable to render DOCX resume", exception);
        }
    }

    private static byte[] canonicalizeDocxPackage(byte[] source, LocalDate artifactDate) throws Exception {
        var parts = new TreeMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(source))) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                var part = new ByteArrayOutputStream();
                input.transferTo(part);
                parts.put(entry.getName(), part.toByteArray());
            }
        }
        try (var output = new ByteArrayOutputStream(); var archive = new ZipOutputStream(output)) {
            for (var part : parts.entrySet()) {
                var entry = new ZipEntry(part.getKey());
                entry.setTimeLocal(artifactDate.atStartOfDay());
                archive.putNextEntry(entry);
                archive.write(part.getValue());
                archive.closeEntry();
            }
            archive.finish();
            return output.toByteArray();
        }
    }

    private static void configureDocument(XWPFDocument document, ResumeDocumentModel model) {
        var properties = document.getProperties().getCoreProperties();
        properties.setTitle(model.contact().fullName() + " Resume");
        properties.setSubjectProperty("Resume for " + model.targetRole() + " at " + model.targetCompany());
        properties.setCreator(PDF_PRODUCER);
        properties.setDescription("ATS single-column resume template " + TEMPLATE_VERSION);
        Date artifactTimestamp = Date.from(model.artifactDate().atStartOfDay(ZoneOffset.UTC).toInstant());
        properties.setCreated(Optional.of(artifactTimestamp));
        properties.setModified(Optional.of(artifactTimestamp));
        properties.setLastModifiedByUser(PDF_PRODUCER);
        properties.setRevision("1");

        CTSectPr section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        pageSize.setW(A4_WIDTH_TWIPS);
        pageSize.setH(A4_HEIGHT_TWIPS);
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(HALF_INCH_TWIPS));
        margins.setRight(BigInteger.valueOf(HALF_INCH_TWIPS));
        margins.setBottom(BigInteger.valueOf(HALF_INCH_TWIPS));
        margins.setLeft(BigInteger.valueOf(HALF_INCH_TWIPS));
        margins.setHeader(BigInteger.valueOf(360));
        margins.setFooter(BigInteger.valueOf(360));
        margins.setGutter(BigInteger.ZERO);
    }

    private static void addName(XWPFDocument document, String name) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        paragraph.setSpacingAfter(30);
        XWPFRun run = paragraph.createRun();
        styleRun(run, 19, true);
        setText(run, name);
    }

    private static void addContact(XWPFDocument document, ResumeContact contact) {
        List<String> values = new ArrayList<>();
        values.add(contact.email());
        if (contact.phone() != null) {
            values.add(contact.phone());
        }
        if (contact.location() != null) {
            values.add(contact.location());
        }
        values.addAll(contact.profileLinks());
        addBodyParagraph(document, String.join(" | ", values), 9, false);
    }

    private static void addTextSection(XWPFDocument document, String title, List<String> paragraphs) {
        if (paragraphs.isEmpty()) {
            return;
        }
        addSectionHeading(document, title);
        paragraphs.forEach(value -> addBodyParagraph(document, value, 10, false));
    }

    private static void addSkills(XWPFDocument document, List<String> skills) {
        if (skills.isEmpty()) {
            return;
        }
        addSectionHeading(document, "SKILLS");
        addBodyParagraph(document, String.join(", ", skills), 10, false);
    }

    private static void addEntrySection(XWPFDocument document, String title, List<ResumeEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        addSectionHeading(document, title);
        entries.forEach(entry -> addEntry(document, entry));
    }

    private static void addSectionHeading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        keepWithNext(paragraph);
        paragraph.setBorderBottom(Borders.SINGLE);
        paragraph.setSpacingBefore(120);
        paragraph.setSpacingAfter(45);
        XWPFRun run = paragraph.createRun();
        styleRun(run, 11, true);
        setText(run, text);
    }

    private static void addEntry(XWPFDocument document, ResumeEntry entry) {
        XWPFParagraph heading = document.createParagraph();
        keepWithNext(heading);
        heading.setSpacingBefore(55);
        heading.setSpacingAfter(10);
        XWPFRun title = heading.createRun();
        styleRun(title, 10, true);
        setText(title, entry.title());
        if (entry.organization() != null) {
            XWPFRun organization = heading.createRun();
            styleRun(organization, 10, false);
            setText(organization, " | " + entry.organization());
        }

        List<String> metadata = new ArrayList<>();
        if (entry.location() != null) {
            metadata.add(entry.location());
        }
        if (entry.dateRange() != null) {
            metadata.add(entry.dateRange());
        }
        if (!metadata.isEmpty()) {
            addBodyParagraph(document, String.join(" | ", metadata), 9, false);
        }
        entry.bullets().forEach(value -> addBullet(document, value));
    }

    private static void addBodyParagraph(XWPFDocument document, String text, int fontSize, boolean bold) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(45);
        paragraph.setSpacingBetween(1.0);
        XWPFRun run = paragraph.createRun();
        styleRun(run, fontSize, bold);
        setText(run, text);
    }

    private static void addBullet(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setIndentationLeft(260);
        paragraph.setIndentationHanging(180);
        paragraph.setSpacingAfter(25);
        XWPFRun run = paragraph.createRun();
        styleRun(run, 10, false);
        setText(run, "• " + text);
    }

    private static void styleRun(XWPFRun run, int fontSize, boolean bold) {
        run.setFontFamily("Arial");
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setColor("111111");
    }

    private static void keepWithNext(XWPFParagraph paragraph) {
        if (!paragraph.getCTP().isSetPPr()) {
            paragraph.getCTP().addNewPPr();
        }
        paragraph.setKeepNext(true);
    }

    private static void setText(XWPFRun run, String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        run.setText(lines[0]);
        for (int index = 1; index < lines.length; index++) {
            run.addBreak();
            run.setText(lines[index]);
        }
    }

    private static void appendContactHtml(StringBuilder html, ResumeContact contact) {
        List<String> values = new ArrayList<>();
        values.add(contact.email());
        if (contact.phone() != null) {
            values.add(contact.phone());
        }
        if (contact.location() != null) {
            values.add(contact.location());
        }
        values.addAll(contact.profileLinks());
        html.append("<p class=\"contact\">");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                html.append(" | ");
            }
            appendEscaped(html, values.get(index));
        }
        html.append("</p>");
    }

    private static void appendTextSection(StringBuilder html, String title, List<String> paragraphs) {
        if (paragraphs.isEmpty()) {
            return;
        }
        html.append("<section><h2>");
        appendEscaped(html, title);
        html.append("</h2>");
        paragraphs.forEach(value -> {
            html.append("<p>");
            appendEscaped(html, value);
            html.append("</p>");
        });
        html.append("</section>");
    }

    private static void appendSkills(StringBuilder html, List<String> skills) {
        if (skills.isEmpty()) {
            return;
        }
        html.append("<section><h2>Skills</h2><p class=\"skills\">");
        for (int index = 0; index < skills.size(); index++) {
            if (index > 0) {
                html.append(", ");
            }
            appendEscaped(html, skills.get(index));
        }
        html.append("</p></section>");
    }

    private static void appendEntrySection(StringBuilder html, String title, List<ResumeEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        html.append("<section><h2>");
        appendEscaped(html, title);
        html.append("</h2>");
        entries.forEach(entry -> appendEntry(html, entry));
        html.append("</section>");
    }

    private static void appendEntry(StringBuilder html, ResumeEntry entry) {
        html.append("<div class=\"entry\"><h3>");
        appendEscaped(html, entry.title());
        html.append("</h3>");
        List<String> metadata = new ArrayList<>();
        if (entry.organization() != null) {
            metadata.add(entry.organization());
        }
        if (entry.location() != null) {
            metadata.add(entry.location());
        }
        if (entry.dateRange() != null) {
            metadata.add(entry.dateRange());
        }
        if (!metadata.isEmpty()) {
            html.append("<p class=\"meta\">");
            for (int index = 0; index < metadata.size(); index++) {
                if (index > 0) {
                    html.append(" | ");
                }
                appendEscaped(html, metadata.get(index));
            }
            html.append("</p>");
        }
        if (!entry.bullets().isEmpty()) {
            html.append("<ul>");
            entry.bullets().forEach(value -> {
                html.append("<li>");
                appendEscaped(html, value);
                html.append("</li>");
            });
            html.append("</ul>");
        }
        html.append("</div>");
    }

    private static void appendEscaped(StringBuilder html, String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            switch (character) {
                case '&' -> html.append("&amp;");
                case '<' -> html.append("&lt;");
                case '>' -> html.append("&gt;");
                case '"' -> html.append("&quot;");
                case '\'' -> html.append("&#39;");
                case '\n' -> html.append("<br />");
                default -> html.append(character);
            }
        }
    }
}
