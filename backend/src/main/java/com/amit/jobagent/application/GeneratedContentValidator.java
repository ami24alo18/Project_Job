package com.amit.jobagent.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Fail-closed validation for model-produced application content.
 *
 * <p>The model does not decide what is factual. Every character-bearing content item must be
 * decomposed into claim atoms, and each atom is checked against the immutable candidate or job
 * evidence appropriate to its declared type.</p>
 */
@Service
public class GeneratedContentValidator {
    private static final int MAX_CONTENT_LENGTH = 12_000;
    private static final int MAX_CLAIM_LENGTH = 2_000;
    private static final Set<String> JOB_FIELDS = Set.of(
            "job.title", "job.company", "job.location", "job.description");
    private static final Pattern TOKEN = Pattern.compile(
            "[\\p{L}\\p{N}+#]+(?:[./'-][\\p{L}\\p{N}+#]+)*");
    private static final Pattern NUMBER_OR_DATE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:\\d{4}-\\d{2}-\\d{2}|\\d+(?:[.,]\\d+)?%?)(?![\\p{L}\\p{N}])");
    private static final Pattern NAMED_ENTITY = Pattern.compile(
            "\\b(?:[A-Z][\\p{L}\\p{N}+.#&'-]+(?:\\s+|$)){2,}");
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?iu)\\b(race|racial|ethnicity|ethnic|religion|religious|gender identity|sexual orientation|"
                    + "disability|disabled|medical|veteran status|criminal history|caste|health information|"
                    + "pregnan(?:t|cy)|genetic information|marital status|date of birth|social security|"
                    + "passport number|national id)\\b");
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
            "(?iu)(ignore|disregard|override|forget).{0,50}(previous|prior|system|developer|instructions?|prompt)"
                    + "|reveal.{0,40}(system|developer|hidden).{0,30}(prompt|instructions?|message)"
                    + "|exfiltrat(?:e|ion)|jailbreak|developer mode|system prompt|tool call|execute (?:code|command)");
    private static final Pattern CANDIDATE_ASSERTION = Pattern.compile(
            "(?iu)\\b(?:i|my|we)\\s+(?:have|had|hold|am|was|worked|built|created|led|managed|developed|"
                    + "implemented|earned|graduated|certified|achieved|improved|reduced|increased|delivered)\\b");

    /* Words permitted as rephrasing glue but never sufficient evidence for a concrete entity/value. */
    private static final Set<String> CANDIDATE_STYLE_WORDS = words(
            "a an and as at by for from in into of on or the through to with without"
                    + " experience experienced expertise skilled focused relevant proven professional"
                    + " strong using use used work worked working deliver delivered delivering"
                    + " build built building create created creating develop developed developing"
                    + " design designed designing implement implemented implementing lead led leading"
                    + " manage managed managing support supported supporting improve improved improving"
                    + " reduce reduced reducing increase increased increasing collaborate collaborated"
                    + " responsible responsibilities role project projects service services system systems"
                    + " application applications solution solutions team teams customer customers"
                    + " successfully including across while quality reliable scalable efficient"
                    + " engineering engineer software backend frontend fullstack full-stack"
                    + " knowledge proficiency proficient familiar hands-on related current previous");
    private static final Set<String> JOB_STYLE_WORDS = words(
            "a an and as at by for from in into of on or the this that their our your to"
                    + " i am excited interested apply applying application opportunity role position"
                    + " opening vacancy join joining team company organization organisation"
                    + " hello hi dear recruiter hiring manager regarding thank thanks consideration"
                    + " believe aligns aligned match matches relevant would welcome discuss discussing"
                    + " look forward looking about with within is are was were be being been");
    private static final Set<String> NON_FACTUAL_WORDS = words(
            "a an and as at by for from in into of on or the this that to"
                    + " i am excited interested apply applying application opportunity role position"
                    + " hello hi dear recruiter hiring manager thank thanks consideration"
                    + " would welcome discuss discussing look forward looking"
                    + " sincerely regards best kind please believe motivated motivation"
                    + " enjoy eager hope appreciate about with within is are was were be being been");

    public GenerationValidationResult validate(
            GeneratedApplicationContent output,
            List<VerifiedFactSnapshot> facts,
            List<String> jobRequirements) {
        return validate(output, facts, legacyEvidence(jobRequirements, Set.of()));
    }

    public GenerationValidationResult validate(
            GeneratedApplicationContent output,
            List<VerifiedFactSnapshot> facts,
            List<String> jobRequirements,
            Set<UUID> eligibleRequirementIds) {
        return validate(output, facts, legacyEvidence(jobRequirements, eligibleRequirementIds));
    }

    public GenerationValidationResult validate(
            GeneratedApplicationContent output,
            List<VerifiedFactSnapshot> facts,
            JobClaimEvidence jobEvidence) {
        var allCodes = new LinkedHashSet<String>();
        var claimCodes = new LinkedHashMap<Integer, List<String>>();
        if (malformed(output)) {
            return new GenerationValidationResult(
                    false, List.of("MALFORMED_STRUCTURED_OUTPUT"), Map.of());
        }

        List<VerifiedFactSnapshot> eligibleFacts = facts == null ? List.of() : facts;
        JobClaimEvidence exactJobEvidence = jobEvidence == null ? JobClaimEvidence.empty() : jobEvidence;
        Map<UUID, VerifiedFactSnapshot> factsById = eligibleFacts.stream()
                .filter(Objects::nonNull)
                .filter(fact -> fact.id() != null)
                .collect(Collectors.toMap(
                        VerifiedFactSnapshot::id,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        Map<String, GeneratedContentItem> contentByKey = validateContents(output.contents(), allCodes);
        Map<String, List<IndexedClaim>> claimsByContent = new LinkedHashMap<>();
        Set<String> seenClaims = new HashSet<>();
        Map<String, String> claimSignatures = new HashMap<>();

        for (int index = 0; index < output.claims().size(); index++) {
            GeneratedClaimAtom claim = output.claims().get(index);
            var codes = new LinkedHashSet<String>();
            validateClaimShape(claim, contentByKey, codes);
            if (claim != null && claim.contentKey() != null) {
                claimsByContent.computeIfAbsent(claim.contentKey(), ignored -> new ArrayList<>())
                        .add(new IndexedClaim(index, claim));
            }
            if (claim != null && claim.claimText() != null && claim.contentKey() != null) {
                String identity = canonical(claim.contentKey()) + "|" + canonical(claim.claimText());
                String signature = claimSignature(claim);
                if (!seenClaims.add(identity)) {
                    codes.add("DUPLICATE_CLAIM");
                    if (!Objects.equals(claimSignatures.get(identity), signature)) {
                        codes.add("CONFLICTING_CLAIM_SOURCES");
                    }
                } else {
                    claimSignatures.put(identity, signature);
                }
            }

            if (claim != null && claim.claimText() != null) {
                if (SENSITIVE.matcher(claim.claimText()).find()) {
                    codes.add("PROHIBITED_SENSITIVE_CONTENT");
                }
                if (PROMPT_INJECTION.matcher(claim.claimText()).find()) {
                    codes.add("PROHIBITED_PROMPT_INJECTION");
                }
            }

            if (claim != null && claim.claimType() != null) {
                switch (claim.claimType()) {
                    case CANDIDATE_FACT -> validateCandidateClaim(claim, factsById, exactJobEvidence, codes);
                    case JOB_REFERENCE -> validateJobClaim(claim, exactJobEvidence, codes);
                    case NON_FACTUAL -> validateNonFactualClaim(claim, codes);
                }
            }
            recordClaimCodes(index, codes, claimCodes, allCodes);
        }

        validateClaimCoverage(contentByKey, claimsByContent, claimCodes, allCodes);
        validateAuxiliaryLists(output, allCodes);
        return new GenerationValidationResult(
                allCodes.isEmpty(), List.copyOf(allCodes), Map.copyOf(claimCodes));
    }

    private static boolean malformed(GeneratedApplicationContent output) {
        return output == null
                || output.contents() == null
                || output.claims() == null
                || output.warnings() == null
                || output.unsupportedRequirements() == null;
    }

    private static Map<String, GeneratedContentItem> validateContents(
            List<GeneratedContentItem> contents, Set<String> allCodes) {
        var contentByKey = new LinkedHashMap<String, GeneratedContentItem>();
        var orders = new HashSet<Integer>();
        if (contents.isEmpty()) {
            allCodes.add("EMPTY_GENERATED_CONTENT");
        }
        for (GeneratedContentItem content : contents) {
            if (content == null
                    || content.type() == null
                    || content.key() == null
                    || content.key().isBlank()
                    || content.key().length() > 150
                    || content.order() < 0
                    || content.text() == null
                    || content.text().isBlank()
                    || content.text().length() > MAX_CONTENT_LENGTH) {
                allCodes.add("CONTENT_SHAPE_OR_LENGTH_INVALID");
                continue;
            }
            if (contentByKey.putIfAbsent(content.key(), content) != null) {
                allCodes.add("DUPLICATE_CONTENT_KEY");
            }
            if (!orders.add(content.order())) {
                allCodes.add("DUPLICATE_CONTENT_ORDER");
            }
            if (SENSITIVE.matcher(content.text()).find()) {
                allCodes.add("PROHIBITED_SENSITIVE_CONTENT");
            }
            if (PROMPT_INJECTION.matcher(content.text()).find()) {
                allCodes.add("PROHIBITED_PROMPT_INJECTION");
            }
        }
        return contentByKey;
    }

    private static void validateClaimShape(
            GeneratedClaimAtom claim,
            Map<String, GeneratedContentItem> contentByKey,
            Set<String> codes) {
        if (claim == null
                || claim.contentKey() == null
                || claim.contentKey().isBlank()
                || claim.claimText() == null
                || claim.claimText().isBlank()
                || claim.claimText().length() > MAX_CLAIM_LENGTH
                || claim.claimType() == null
                || claim.factIds() == null
                || claim.requirementIds() == null) {
            codes.add("CLAIM_MALFORMED");
        }
        if (claim != null
                && (claim.contentKey() == null || !contentByKey.containsKey(claim.contentKey()))) {
            codes.add("UNKNOWN_CONTENT_KEY");
        }
    }

    private static void validateCandidateClaim(
            GeneratedClaimAtom claim,
            Map<UUID, VerifiedFactSnapshot> factsById,
            JobClaimEvidence jobEvidence,
            Set<String> codes) {
        List<UUID> factIds = claim.factIds() == null ? List.of() : claim.factIds();
        if (factIds.isEmpty()) {
            codes.add("MISSING_CANDIDATE_FACT_EVIDENCE");
        }
        if (claim.jobFieldReference() != null && !claim.jobFieldReference().isBlank()) {
            codes.add("CANDIDATE_CLAIM_HAS_JOB_FIELD_REFERENCE");
        }
        var referenced = new ArrayList<VerifiedFactSnapshot>();
        var uniqueIds = new HashSet<UUID>();
        for (UUID id : factIds) {
            if (id == null || !uniqueIds.add(id)) {
                codes.add("DUPLICATE_OR_NULL_FACT_ID");
                continue;
            }
            VerifiedFactSnapshot fact = factsById.get(id);
            if (fact == null) {
                codes.add("UNKNOWN_OR_WRONG_VERSION_FACT_ID");
            } else {
                referenced.add(fact);
            }
        }
        if (claim.requirementIds() != null) {
            for (UUID requirementId : claim.requirementIds()) {
                if (requirementId == null || !jobEvidence.requirements().containsKey(requirementId)) {
                    codes.add("UNKNOWN_JOB_REQUIREMENT_ID");
                }
            }
        }
        if (claim.claimText() == null || referenced.isEmpty()) {
            return;
        }

        String rawEvidence = referenced.stream()
                .map(GeneratedContentValidator::factEvidence)
                .collect(Collectors.joining(" "));
        Set<String> evidenceTokens = new LinkedHashSet<>(tokens(rawEvidence));
        Set<String> unsupported = tokens(claim.claimText()).stream()
                .filter(token -> !evidenceTokens.contains(token))
                .filter(token -> !CANDIDATE_STYLE_WORDS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unsupported.isEmpty()) {
            codes.add("UNSUPPORTED_CANDIDATE_LANGUAGE");
        }

        for (String value : numericValues(claim.claimText())) {
            if (!containsPhrase(rawEvidence, value)) {
                codes.add("UNSUPPORTED_NUMERIC_OR_DATE_VALUE");
            }
        }

        Set<String> knownSkills = referenced.stream()
                .flatMap(fact -> fact.skillTags() == null ? java.util.stream.Stream.empty() : fact.skillTags().stream())
                .map(GeneratedContentValidator::canonical)
                .collect(Collectors.toSet());
        for (String token : unsupported) {
            if (looksLikeTechnology(token, claim.claimText()) || knownSkills.contains(token)) {
                codes.add("UNSUPPORTED_SKILL_OR_TECHNOLOGY");
            }
        }

        var entities = NAMED_ENTITY.matcher(claim.claimText());
        while (entities.find()) {
            String entity = entities.group().trim();
            if (!containsPhrase(rawEvidence, entity)) {
                codes.add("UNSUPPORTED_ORGANIZATION_TITLE_OR_NAME");
            }
        }
    }

    private static void validateJobClaim(
            GeneratedClaimAtom claim,
            JobClaimEvidence evidence,
            Set<String> codes) {
        if (claim.factIds() != null && !claim.factIds().isEmpty()) {
            codes.add("JOB_CLAIM_HAS_CANDIDATE_FACT_IDS");
        }
        var sourceText = new ArrayList<String>();
        var requirementIds = claim.requirementIds() == null ? List.<UUID>of() : claim.requirementIds();
        for (UUID id : requirementIds) {
            String requirement = evidence.requirements().get(id);
            if (id == null || requirement == null) {
                codes.add("UNKNOWN_JOB_REQUIREMENT_ID");
            } else {
                sourceText.add(requirement);
            }
        }
        List<String> fields = parseJobFields(claim.jobFieldReference(), codes);
        for (String field : fields) {
            String value = evidence.fields().get(field);
            if (value == null || value.isBlank()) {
                codes.add("UNKNOWN_JOB_FIELD_REFERENCE");
            } else {
                sourceText.add(value);
            }
        }
        if (requirementIds.isEmpty() && fields.isEmpty()) {
            codes.add("MISSING_JOB_CLAIM_EVIDENCE");
        }
        if (claim.claimText() == null || sourceText.isEmpty()) {
            return;
        }
        if (CANDIDATE_ASSERTION.matcher(claim.claimText()).find()
                && !canonical(claim.claimText()).contains("i am excited")
                && !canonical(claim.claimText()).contains("i am interested")) {
            codes.add("CANDIDATE_ASSERTION_MISCLASSIFIED_AS_JOB_REFERENCE");
        }
        Set<String> evidenceTokens = new LinkedHashSet<>(tokens(String.join(" ", sourceText)));
        boolean ungrounded = tokens(claim.claimText()).stream()
                .anyMatch(token -> !evidenceTokens.contains(token) && !JOB_STYLE_WORDS.contains(token));
        if (ungrounded) {
            codes.add("UNSUPPORTED_JOB_REFERENCE_LANGUAGE");
        }
        String rawEvidence = String.join(" ", sourceText);
        for (String value : numericValues(claim.claimText())) {
            if (!containsPhrase(rawEvidence, value)) {
                codes.add("UNSUPPORTED_JOB_REFERENCE_VALUE");
            }
        }
    }

    private static void validateNonFactualClaim(GeneratedClaimAtom claim, Set<String> codes) {
        if (claim.factIds() != null && !claim.factIds().isEmpty()) {
            codes.add("NON_CANDIDATE_CLAIM_HAS_FACT_IDS");
        }
        if (claim.requirementIds() != null && !claim.requirementIds().isEmpty()) {
            codes.add("NON_FACTUAL_CLAIM_HAS_JOB_REQUIREMENT_IDS");
        }
        if (claim.jobFieldReference() != null && !claim.jobFieldReference().isBlank()) {
            codes.add("NON_FACTUAL_CLAIM_HAS_JOB_FIELD_REFERENCE");
        }
        if (claim.claimText() == null) {
            return;
        }
        String normalizedClaim = canonical(claim.claimText());
        boolean candidateAssertion = CANDIDATE_ASSERTION.matcher(claim.claimText()).find()
                && !normalizedClaim.contains("i am excited")
                && !normalizedClaim.contains("i am interested");
        if (candidateAssertion
                || !numericValues(claim.claimText()).isEmpty()
                || tokens(claim.claimText()).stream().anyMatch(token -> !NON_FACTUAL_WORDS.contains(token))) {
            codes.add("FACTUAL_LANGUAGE_MISCLASSIFIED_AS_NON_FACTUAL");
        }
    }

    private static void validateClaimCoverage(
            Map<String, GeneratedContentItem> contentByKey,
            Map<String, List<IndexedClaim>> claimsByContent,
            Map<Integer, List<String>> claimCodes,
            Set<String> allCodes) {
        for (GeneratedContentItem content : contentByKey.values()) {
            List<IndexedClaim> contentClaims = claimsByContent.getOrDefault(content.key(), List.of());
            if (contentClaims.isEmpty()) {
                allCodes.add("CONTENT_WITHOUT_CLAIM_ATOMS");
                continue;
            }
            var remaining = tokenCounts(content.text());
            for (IndexedClaim indexed : contentClaims) {
                GeneratedClaimAtom claim = indexed.claim();
                if (claim.claimText() == null || claim.claimText().isBlank()) {
                    continue;
                }
                if (!containsCanonicalPhrase(content.text(), claim.claimText())) {
                    addClaimCode(indexed.index(), "CLAIM_TEXT_NOT_IN_CONTENT", claimCodes, allCodes);
                    continue;
                }
                for (String token : tokens(claim.claimText())) {
                    remaining.computeIfPresent(token, (ignored, count) -> count <= 1 ? null : count - 1);
                }
            }
            if (!remaining.isEmpty()) {
                allCodes.add("UNCLAIMED_CONTENT_TEXT");
            }
        }
    }

    private static void validateAuxiliaryLists(
            GeneratedApplicationContent output, Set<String> allCodes) {
        if (output.warnings().stream().anyMatch(value -> value == null || value.length() > 2_000)
                || output.unsupportedRequirements().stream()
                        .anyMatch(value -> value == null || value.length() > 2_000)) {
            allCodes.add("AUXILIARY_CONTENT_INVALID");
        }
    }

    private static List<String> parseJobFields(String raw, Set<String> codes) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        var fields = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (fields.isEmpty() || fields.stream().anyMatch(field -> !JOB_FIELDS.contains(field))) {
            codes.add("UNSUPPORTED_JOB_FIELD_REFERENCE");
        }
        return fields.stream().filter(JOB_FIELDS::contains).toList();
    }

    private static String factEvidence(VerifiedFactSnapshot fact) {
        return String.join(" ",
                Objects.toString(fact.statement(), ""),
                Objects.toString(fact.company(), ""),
                Objects.toString(fact.startDate(), ""),
                Objects.toString(fact.endDate(), ""),
                fact.skillTags() == null ? "" : String.join(" ", fact.skillTags()),
                fact.domainTags() == null ? "" : String.join(" ", fact.domainTags()));
    }

    private static String claimSignature(GeneratedClaimAtom claim) {
        return claim.claimType() + "|"
                + Objects.toString(claim.factIds(), "") + "|"
                + Objects.toString(claim.requirementIds(), "") + "|"
                + canonical(claim.jobFieldReference());
    }

    private static void recordClaimCodes(
            int index,
            Set<String> codes,
            Map<Integer, List<String>> claimCodes,
            Set<String> allCodes) {
        if (!codes.isEmpty()) {
            claimCodes.put(index, List.copyOf(codes));
            allCodes.addAll(codes);
        }
    }

    private static void addClaimCode(
            int index,
            String code,
            Map<Integer, List<String>> claimCodes,
            Set<String> allCodes) {
        var updated = new LinkedHashSet<>(claimCodes.getOrDefault(index, List.of()));
        updated.add(code);
        claimCodes.put(index, List.copyOf(updated));
        allCodes.add(code);
    }

    private static JobClaimEvidence legacyEvidence(
            List<String> requirements, Set<UUID> requirementIds) {
        List<String> safeRequirements = requirements == null ? List.of() : requirements;
        List<UUID> ids = requirementIds == null ? List.of() : new ArrayList<>(requirementIds);
        var mapped = new LinkedHashMap<UUID, String>();
        for (int index = 0; index < Math.min(ids.size(), safeRequirements.size()); index++) {
            mapped.put(ids.get(index), safeRequirements.get(index));
        }
        return new JobClaimEvidence(mapped, Map.of());
    }

    private static Map<String, Integer> tokenCounts(String text) {
        var counts = new LinkedHashMap<String, Integer>();
        tokens(text).forEach(token -> counts.merge(token, 1, Integer::sum));
        return counts;
    }

    private static List<String> tokens(String text) {
        var result = new ArrayList<String>();
        var matcher = TOKEN.matcher(Normalizer.normalize(Objects.toString(text, ""), Normalizer.Form.NFKC));
        while (matcher.find()) {
            result.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private static List<String> numericValues(String text) {
        var values = new ArrayList<String>();
        var matcher = NUMBER_OR_DATE.matcher(Objects.toString(text, ""));
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return values;
    }

    private static boolean looksLikeTechnology(String normalizedToken, String originalText) {
        if (normalizedToken.contains("+") || normalizedToken.contains("#") || normalizedToken.contains(".")) {
            return true;
        }
        return Pattern.compile("(?u)(?<![\\p{L}\\p{N}])" + Pattern.quote(normalizedToken)
                        + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE)
                .matcher(originalText)
                .results()
                .map(result -> result.group())
                .anyMatch(value -> value.length() > 1 && Character.isUpperCase(value.charAt(0)));
    }

    private static boolean containsPhrase(String text, String phrase) {
        return Pattern.compile(
                        "(?iu)(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])")
                .matcher(Objects.toString(text, ""))
                .find();
    }

    private static boolean containsCanonicalPhrase(String text, String phrase) {
        String haystack = " " + String.join(" ", tokens(text)) + " ";
        String needle = " " + String.join(" ", tokens(phrase)) + " ";
        return needle.length() > 2 && haystack.contains(needle);
    }

    private static Set<String> words(String raw) {
        return Set.copyOf(Arrays.asList(raw.split("\\s+")));
    }

    private static String canonical(String value) {
        return String.join(" ", tokens(value));
    }

    private record IndexedClaim(int index, GeneratedClaimAtom claim) {}
}
