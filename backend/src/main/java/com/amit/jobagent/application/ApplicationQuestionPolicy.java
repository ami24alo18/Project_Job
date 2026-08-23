package com.amit.jobagent.application;

import com.amit.jobagent.profile.version.PublishedProfileSnapshot;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ApplicationQuestionPolicy {
    private static final Pattern SENSITIVE_DEMOGRAPHIC = Pattern.compile(
            "(?iu)\\b(?:race|racial identity|ethnic(?:ity)?|religion|religious belief|gender|gender identity|"
                    + "sex|sexual orientation|disabilit(?:y|ies)|medical (?:condition|history|information)|"
                    + "health information|veteran|criminal (?:history|record)|caste|marital status|"
                    + "equal opportunity|demographic(?: data| information| question| status)?|"
                    + "national origin|nationality|genetic (?:data|information|history)|"
                    + "pregnan(?:t|cy)|expecting a child|date of birth|birth date|birth year|year of birth|"
                    + "place of birth|country of origin|d\\.?o\\.?b\\.?|how old|your age|age range|"
                    + "born (?:on|in|when|where)|over \\d{1,3}|under \\d{1,3}|at least \\d{1,3}|"
                    + "older than \\d{1,3}|younger than \\d{1,3})\\b");
    private static final Pattern LEGAL_CONFIRMATION = Pattern.compile(
            "(?iu)\\b(?:consent|attest|attestation|swear|affirm under|under penalty|legally binding|"
                    + "electronic signature|e-signature|signature|sign (?:this|below|electronically|the application)|"
                    + "declare(?:d|s|ation)?|"
                    + "acknowledge(?:ment)?|certif(?:y|ies|ied)(?: that)?|"
                    + "confirm(?: that)?.{0,60}(?:application|information|answers?|statements?|true|accurate|complete)|"
                    + "agree to (?:the )?(?:terms|conditions|policy|declaration)|"
                    + "accept (?:the )?(?:terms|conditions|policy|declaration)|"
                    + "authorize (?:the |this |your |us |employer|company|background|screening|processing|collection|use)|"
                    + "authorization (?:for|to).{0,40}(?:background|screening|data|processing|collection|use))\\b");
    private static final Pattern DECISION = Pattern.compile(
            "(?iu)\\b(?:compensation|salary|pay|sponsor|work authori[sz]ation|authori[sz]ed to work|citizen(?:ship)?|"
                    + "relocat|available to start|start date|notice period)\\b");
    private static final Pattern MOTIVATION = Pattern.compile(
            "(?iu)(?:\\bwhy\\b.{0,80}\\b(?:interest(?:ed)?|want|apply|role|position|company|organization|join)\\b"
                    + "|\\bmotivat(?:e|ed|es|ion)\\b|\\binterest(?:ed)? in\\b)");
    private static final Pattern EXPERIENCE = Pattern.compile(
            "(?iu)\\b(?:experience|background|qualified|qualification|skills?|technolog(?:y|ies)|"
                    + "projects?|achievements?|accomplishments?|education|certifications?)\\b");
    private static final Pattern PROJECT = Pattern.compile("(?iu)\\bprojects?\\b");
    private static final Pattern ACHIEVEMENT = Pattern.compile("(?iu)\\b(?:achievements?|accomplishments?)\\b");
    private static final Pattern EDUCATION = Pattern.compile("(?iu)\\beducation\\b");
    private static final Pattern CERTIFICATION = Pattern.compile("(?iu)\\bcertifications?\\b");
    private static final Pattern SKILL = Pattern.compile("(?iu)\\b(?:skills?|technolog(?:y|ies))\\b");
    private static final Set<String> NON_SEMANTIC_TERMS = Set.of(
            "a", "about", "an", "and", "application", "apply", "are", "at", "be", "candidate", "company",
            "describe", "do", "does", "for", "have", "how", "in", "interested", "interest", "is", "job",
            "motivation", "motivated", "my", "of", "organization", "position", "preferred", "project", "projects",
            "qualification", "qualifications", "qualified", "relevant", "required", "requires", "role", "skill",
            "skills", "summarize", "tell", "technology", "technologies", "the", "this", "to", "us", "want",
            "what", "why", "with", "work", "working", "you", "your", "experience", "background");

    public QuestionClassificationResult classify(String question, PublishedProfileSnapshot profile) {
        String value = Objects.toString(question, "").trim();
        if (SENSITIVE_DEMOGRAPHIC.matcher(value).find() || LEGAL_CONFIRMATION.matcher(value).find()) {
            return new QuestionClassificationResult(
                    QuestionClassification.SENSITIVE_NEVER_AUTOMATIC,
                    null,
                    QuestionAnswerStatus.BLOCKED_SENSITIVE,
                    null);
        }
        if (DECISION.matcher(value).find()) {
            return new QuestionClassificationResult(
                    QuestionClassification.USER_INPUT_REQUIRED,
                    null,
                    QuestionAnswerStatus.USER_INPUT_REQUIRED,
                    null);
        }
        var core = profile.snapshot().path("profile");
        if (value.matches("(?iu).*(?:\\b(?:current )?location\\b|\\bcity are you (?:currently )?located\\b).*")
                && !core.path("currentLocation").isMissingNode()
                && !core.path("currentLocation").isNull()
                && !core.path("currentLocation").asText().isBlank()) {
            return new QuestionClassificationResult(
                    QuestionClassification.VERIFIED_AUTOMATIC,
                    core.path("currentLocation").asText(),
                    QuestionAnswerStatus.DRAFTED,
                    100);
        }
        if (value.matches("(?iu).*\\b(years?|total).*experience.*")) {
            int months = core.path("totalExperienceMonths").asInt(-1);
            if (months >= 0) {
                return new QuestionClassificationResult(
                        QuestionClassification.VERIFIED_AUTOMATIC,
                        formatYears(months),
                        QuestionAnswerStatus.DRAFTED,
                        100);
            }
        }
        return new QuestionClassificationResult(
                QuestionClassification.SUGGESTED_REQUIRES_REVIEW,
                null,
                QuestionAnswerStatus.USER_INPUT_REQUIRED,
                60);
    }

    Optional<SuggestedQuestionDraft> draftSuggested(
            String question, List<VerifiedFactSnapshot> facts, List<String> jobContext) {
        String value = Objects.toString(question, "").trim();
        if (value.isBlank()
                || SENSITIVE_DEMOGRAPHIC.matcher(value).find()
                || LEGAL_CONFIRMATION.matcher(value).find()
                || DECISION.matcher(value).find()
                || (!MOTIVATION.matcher(value).find() && !EXPERIENCE.matcher(value).find())) {
            return Optional.empty();
        }

        var questionTerms = semanticTerms(value);
        var semanticTerms = new LinkedHashSet<>(questionTerms);
        if (jobContext != null) {
            jobContext.forEach(context -> semanticTerms.addAll(semanticTerms(context)));
        }
        if (semanticTerms.isEmpty()) {
            return Optional.empty();
        }

        VerifiedFactSnapshot selected = null;
        int selectedScore = 0;
        for (var fact : facts == null ? List.<VerifiedFactSnapshot>of() : facts) {
            if (fact == null || !eligibleCategory(value, fact)) {
                continue;
            }
            var factTerms = factTerms(fact);
            if (!questionTerms.isEmpty() && questionTerms.stream().noneMatch(factTerms::contains)) {
                continue;
            }
            int score = (int) semanticTerms.stream().filter(factTerms::contains).count();
            if (score > selectedScore) {
                selected = fact;
                selectedScore = score;
            }
        }
        if (selected == null) {
            return Optional.empty();
        }

        String prefix = MOTIVATION.matcher(value).find()
                ? "Interested in this role:"
                : "For this role:";
        return Optional.of(new SuggestedQuestionDraft(prefix + " " + selected.statement(), prefix, selected));
    }

    private static boolean eligibleCategory(String question, VerifiedFactSnapshot fact) {
        String category = Objects.toString(fact.category(), "").toUpperCase(Locale.ROOT);
        if (PROJECT.matcher(question).find()) return "PROJECT".equals(category);
        if (ACHIEVEMENT.matcher(question).find()) return "ACHIEVEMENT".equals(category);
        if (EDUCATION.matcher(question).find()) return "EDUCATION".equals(category);
        if (CERTIFICATION.matcher(question).find()) return "CERTIFICATION".equals(category);
        if (SKILL.matcher(question).find()) return fact.skillTags() != null && !fact.skillTags().isEmpty();
        return !"OTHER".equals(category);
    }

    private static Set<String> factTerms(VerifiedFactSnapshot fact) {
        var result = semanticTerms(String.join(" ",
                Objects.toString(fact.statement(), ""),
                Objects.toString(fact.company(), ""),
                String.join(" ", fact.skillTags() == null ? Set.of() : fact.skillTags()),
                String.join(" ", fact.domainTags() == null ? Set.of() : fact.domainTags())));
        return Set.copyOf(result);
    }

    private static LinkedHashSet<String> semanticTerms(String text) {
        var result = new LinkedHashSet<String>();
        String normalized = Normalizer.normalize(Objects.toString(text, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        for (String token : normalized.split("[^\\p{L}\\p{N}+#.]+")) {
            String value = token.trim();
            if (value.length() >= 2 && !NON_SEMANTIC_TERMS.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private static String formatYears(int months) {
        int years = months / 12;
        int remainder = months % 12;
        if (remainder == 0) return years + " year" + (years == 1 ? "" : "s");
        return years + " year" + (years == 1 ? "" : "s")
                + " and " + remainder + " month" + (remainder == 1 ? "" : "s");
    }

    record SuggestedQuestionDraft(String answer, String nonFactualText, VerifiedFactSnapshot fact) {}
}
