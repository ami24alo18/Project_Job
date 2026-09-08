package com.amit.jobagent.profile.version;

import com.amit.jobagent.document.ActiveResumeEvidence;
import com.amit.jobagent.resumefact.ResumeFactCategory;
import com.amit.jobagent.resumefact.ResumeFactResponse;
import com.amit.jobagent.resumefact.ResumeFactSourceType;
import com.amit.jobagent.resumefact.ResumeFactStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Converts extracted resume text into stable, source-linked evidence blocks. */
@Component
class ResumeEvidenceExtractor {
    private static final int MAX_FACTS = 120;
    private static final int MAX_STATEMENT = 1_500;
    private static final Pattern BULLET = Pattern.compile("^[\\s]*[•●▪◦‣⁃*-]\\s+");
    private static final Pattern EMPLOYER_WITH_DATES = Pattern.compile(
            "(?iu).*(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|"
                    + "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|"
                    + "\\b(?:19|20)\\d{2}\\b).*(?:present|current|(?:19|20)\\d{2}).*");

    List<ResumeFactResponse> extract(UUID profileId, ActiveResumeEvidence resume) {
        var drafts = parse(resume.extractedText());
        Set<String> resumeSkills = drafts.stream()
                .filter(draft -> draft.category() == ResumeFactCategory.SKILL)
                .flatMap(draft -> skillTags(draft.statement()).stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var facts = new ArrayList<ResumeFactResponse>();
        for (DraftEvidence draft : drafts) {
            if (facts.size() >= MAX_FACTS) break;
            String statement = draft.statement().length() > MAX_STATEMENT
                    ? draft.statement().substring(0, MAX_STATEMENT)
                    : draft.statement();
            UUID id = UUID.nameUUIDFromBytes((resume.checksum() + ":" + draft.sourceLine() + ":" + statement)
                    .getBytes(StandardCharsets.UTF_8));
            Set<String> skills = matchingSkills(statement, resumeSkills);
            facts.add(new ResumeFactResponse(id, profileId, draft.category(), statement, draft.company(), null, null,
                    skills, Set.of(), ResumeFactStatus.VERIFIED, ResumeFactSourceType.RESUME_DOCUMENT,
                    resume.documentId(), resume.fileName() + ":line " + draft.sourceLine(), statement,
                    resume.createdAt(), "active-resume-extractor", 0L, resume.createdAt(), resume.createdAt()));
        }
        return List.copyOf(facts);
    }

    private static List<DraftEvidence> parse(String extractedText) {
        String[] lines = extractedText.split("\\R", -1);
        var drafts = new ArrayList<DraftEvidence>();
        ResumeFactCategory section = ResumeFactCategory.OTHER;
        boolean insideResumeSection = false;
        String currentCompany = null;
        for (int index = 0; index < lines.length;) {
            String line = clean(lines[index]);
            if (line.isBlank()) { index++; continue; }
            ResumeFactCategory heading = heading(line);
            if (heading != null) {
                section = heading;
                insideResumeSection = true;
                if (section != ResumeFactCategory.EMPLOYMENT) currentCompany = null;
                index++;
                continue;
            }
            if (!insideResumeSection || !useful(line)) { index++; continue; }

            int sourceLine = index + 1;
            if (section == ResumeFactCategory.EMPLOYMENT && EMPLOYER_WITH_DATES.matcher(line).matches()) {
                currentCompany = companyFrom(line);
            }

            if (BULLET.matcher(lines[index]).find()) {
                StringBuilder statement = new StringBuilder(line);
                int next = index + 1;
                while (next < lines.length) {
                    String continuation = clean(lines[next]);
                    if (continuation.isBlank()) { next++; continue; }
                    if (heading(continuation) != null || BULLET.matcher(lines[next]).find()) break;
                    if (section == ResumeFactCategory.EMPLOYMENT
                            && EMPLOYER_WITH_DATES.matcher(continuation).matches()) break;
                    statement.append(' ').append(continuation);
                    next++;
                }
                drafts.add(new DraftEvidence(section, clean(statement.toString()), currentCompany, sourceLine));
                index = next;
                continue;
            }

            if (section == ResumeFactCategory.OTHER) {
                StringBuilder paragraph = new StringBuilder(line);
                int next = index + 1;
                while (next < lines.length) {
                    String continuation = clean(lines[next]);
                    if (continuation.isBlank()) { next++; continue; }
                    if (heading(continuation) != null) break;
                    paragraph.append(' ').append(continuation);
                    next++;
                }
                drafts.add(new DraftEvidence(section, clean(paragraph.toString()), null, sourceLine));
                index = next;
                continue;
            }

            drafts.add(new DraftEvidence(section, line, currentCompany, sourceLine));
            index++;
        }
        return List.copyOf(drafts);
    }

    private static String clean(String value) {
        return value.replaceAll("^[\\s•●▪◦‣⁃*\\-]+", "")
                .replaceAll("[\\p{Cc}&&[^\\t]]", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean useful(String line) {
        if (line.length() < 3) return false;
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.matches("^(page )?\\d+( of \\d+)?$")) return false;
        return !lower.matches("^(resume|curriculum vitae|cv)$");
    }

    private static ResumeFactCategory heading(String line) {
        String h = line.toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", "").replaceAll("\\s+", " ").trim();
        if (h.length() > 45) return null;
        if (Set.of("EXPERIENCE", "WORK EXPERIENCE", "PROFESSIONAL EXPERIENCE", "EMPLOYMENT", "EMPLOYMENT HISTORY").contains(h)) return ResumeFactCategory.EMPLOYMENT;
        if (Set.of("PROJECT", "PROJECTS", "PERSONAL PROJECTS", "PROFESSIONAL PROJECTS").contains(h)) return ResumeFactCategory.PROJECT;
        if (Set.of("EDUCATION", "ACADEMICS", "ACADEMIC BACKGROUND").contains(h)) return ResumeFactCategory.EDUCATION;
        if (Set.of("SKILL", "SKILLS", "TECHNOLOGY", "TECHNOLOGIES", "TECHNICAL SKILLS", "CORE COMPETENCIES").contains(h)) return ResumeFactCategory.SKILL;
        if (Set.of("CERTIFICATION", "CERTIFICATIONS", "LICENSE", "LICENSES").contains(h)) return ResumeFactCategory.CERTIFICATION;
        if (Set.of("ACHIEVEMENT", "ACHIEVEMENTS", "AWARD", "AWARDS").contains(h)) return ResumeFactCategory.ACHIEVEMENT;
        if (Set.of("SUMMARY", "PROFILE SUMMARY", "PROFESSIONAL SUMMARY", "PROFILE", "OBJECTIVE").contains(h)) return ResumeFactCategory.OTHER;
        return null;
    }

    private static String companyFrom(String statement) {
        String company = statement.replaceFirst(
                "(?iu)\\s+(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|"
                        + "aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|(?:19|20)\\d{2}).*$",
                "").trim();
        return company.isBlank() ? null : company;
    }

    private static Set<String> skillTags(String statement) {
        var tags = new LinkedHashSet<String>();
        for (String value : statement.replaceFirst("(?iu)^[^:]{1,35}:\\s*", "").split("[,|;/]")) {
            String tag = value.trim();
            if (tag.length() >= 2 && tag.length() <= 60) tags.add(tag);
        }
        return Set.copyOf(tags);
    }

    private static Set<String> matchingSkills(String statement, Set<String> resumeSkills) {
        if (resumeSkills.isEmpty()) return Set.of();
        String normalized = statement.toLowerCase(Locale.ROOT);
        var matches = new LinkedHashMap<String, String>();
        for (String skill : resumeSkills) {
            if (normalized.contains(skill.toLowerCase(Locale.ROOT))) matches.putIfAbsent(skill.toLowerCase(Locale.ROOT), skill);
        }
        return Set.copyOf(matches.values());
    }

    private record DraftEvidence(
            ResumeFactCategory category, String statement, String company, int sourceLine) {}
}
