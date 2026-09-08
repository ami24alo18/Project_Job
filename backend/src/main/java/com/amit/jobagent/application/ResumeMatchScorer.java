package com.amit.jobagent.application;

import com.amit.jobagent.job.JobMatchingView;
import com.amit.jobagent.matching.EvaluationRequirementProvider;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** A deterministic, zero-token comparison of how visibly the source and draft cover the same JD. */
@Component
class ResumeMatchScorer {
    static final String FALLBACK_METHOD = "JD_KEYWORD_COVERAGE_V1_FALLBACK";
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}+#.]+(?:[-/][\\p{L}\\p{N}+#.]+)*");
    private static final Set<String> SHORT_TERMS = Set.of(
            "ai", "ml", "ui", "ux", "qa", "gc", "jvm", "jpa", "sql", "aws", "gcp", "api", "oop", "tcp", "ip");
    private static final Set<String> STOP_WORDS = Set.of(
            "about", "above", "across", "after", "against", "also", "among", "and", "any", "are", "based",
            "because", "been", "being", "below", "between", "both", "but", "candidate", "company", "could",
            "day", "each", "employee", "employees", "ensure", "excellent", "experience", "for", "from", "good",
            "have", "having", "into", "job", "more", "must", "our", "position", "preferred", "required", "requirements",
            "responsibilities", "responsibility", "role", "should", "skills", "strong", "team", "that", "the", "their",
            "them", "then", "these", "they", "this", "through", "using", "very", "want", "what", "when", "where",
            "which", "while", "will", "with", "within", "work", "working", "would", "years", "you", "your");
    private static final Set<GeneratedContentType> RESUME_TYPES = Set.of(
            GeneratedContentType.RESUME_HEADLINE,
            GeneratedContentType.PROFESSIONAL_SUMMARY,
            GeneratedContentType.SKILL_SECTION,
            GeneratedContentType.EXPERIENCE_BULLET,
            GeneratedContentType.PROJECT_BULLET,
            GeneratedContentType.EDUCATION_SECTION);
    private final SemanticResumeMatchProvider semanticScorer;

    ResumeMatchScorer(SemanticResumeMatchProvider semanticScorer) {
        this.semanticScorer = semanticScorer;
    }

    ResumeMatchComparison compare(
            JobMatchingView job,
            List<EvaluationRequirementProvider.RequirementView> requirements,
            List<VerifiedFactSnapshot> facts,
            List<GeneratedContent> generatedContents) {
        Map<String, Integer> target = new LinkedHashMap<>();
        addWeighted(target, job.title(), 4);
        List<EvaluationRequirementProvider.RequirementView> requirementList =
                requirements == null ? List.of() : requirements;
        for (var requirement : requirementList) {
            int weight = "REQUIRED".equalsIgnoreCase(requirement.type()) ? 4 : 3;
            addWeighted(target, requirement.text(), weight);
        }
        // Evaluated requirements are the immutable, structured representation of the JD. Fall back
        // to raw description text only for legacy evaluations that did not store requirements.
        if (requirementList.isEmpty()) addWeighted(target, job.description(), 1);

        String jobDocument = jobDocument(job, requirementList);

        Set<String> sourceTokens = new LinkedHashSet<>();
        var sourceDocument = new StringBuilder();
        for (VerifiedFactSnapshot fact : facts == null ? List.<VerifiedFactSnapshot>of() : facts) {
            String factText = String.join(" ", Objects.toString(fact.statement(), ""),
                    Objects.toString(fact.company(), ""), Objects.toString(fact.skillTags(), ""),
                    Objects.toString(fact.domainTags(), ""));
            sourceTokens.addAll(tokens(factText));
            sourceDocument.append(factText).append('\n');
        }
        Set<String> draftTokens = new LinkedHashSet<>(sourceTokens);
        var draftDocument = new StringBuilder(sourceDocument);
        for (GeneratedContent content : generatedContents == null ? List.<GeneratedContent>of() : generatedContents) {
            if (RESUME_TYPES.contains(content.type)) {
                draftTokens.addAll(tokens(content.text));
                draftDocument.append(content.text).append('\n');
            }
        }

        var semantic = semanticScorer.score(jobDocument, sourceDocument.toString(), draftDocument.toString());
        int current = semantic.map(SemanticResumeMatchProvider.Scores::currentResumeScore)
                .orElseGet(() -> score(target, sourceTokens));
        int draft = semantic.map(SemanticResumeMatchProvider.Scores::generatedResumeScore)
                .orElseGet(() -> score(target, draftTokens));
        List<String> matched = ranked(target, draftTokens, true, 16);
        List<String> missing = ranked(target, draftTokens, false, 16);
        return new ResumeMatchComparison(current, draft, draft - current,
                semantic.map(SemanticResumeMatchProvider.Scores::method).orElse(FALLBACK_METHOD), matched, missing);
    }

    private static String jobDocument(
            JobMatchingView job, List<EvaluationRequirementProvider.RequirementView> requirements) {
        var document = new StringBuilder();
        document.append(Objects.toString(job.title(), "")).append('\n');
        document.append(Objects.toString(job.company(), "")).append('\n');
        if (requirements.isEmpty()) document.append(Objects.toString(job.description(), ""));
        else requirements.forEach(requirement -> document.append(requirement.text()).append('\n'));
        return document.toString();
    }

    private static void addWeighted(Map<String, Integer> target, String text, int weight) {
        for (String token : tokens(text)) target.merge(token, weight, Math::max);
    }

    private static int score(Map<String, Integer> target, Set<String> document) {
        int possible = target.values().stream().mapToInt(Integer::intValue).sum();
        if (possible == 0) return 0;
        int covered = target.entrySet().stream()
                .filter(entry -> document.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .sum();
        return Math.clamp((int) Math.round(covered * 100.0 / possible), 0, 100);
    }

    private static List<String> ranked(
            Map<String, Integer> target, Set<String> document, boolean present, int limit) {
        return target.entrySet().stream()
                .filter(entry -> document.contains(entry.getKey()) == present)
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .limit(limit)
                .toList();
    }

    private static Set<String> tokens(String text) {
        var result = new LinkedHashSet<String>();
        var matcher = TOKEN.matcher(Normalizer.normalize(Objects.toString(text, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String value = matcher.group().replaceAll("^[.]+|[.]+$", "");
            if ((value.length() >= 3 || SHORT_TERMS.contains(value)) && !STOP_WORDS.contains(value)
                    && !value.matches("\\d+")) result.add(value);
        }
        return result;
    }
}
