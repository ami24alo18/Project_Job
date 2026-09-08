package com.amit.jobagent.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Retains model-authored sections that can be proved from immutable evidence and substitutes only
 * the sections that fail validation. This avoids throwing away an otherwise useful tailored resume
 * because one cover-letter sentence or one claim atom was malformed.
 */
@Component
class EvidenceBoundContentMerger {
    static final String REBOUND_WARNING = "MODEL_CONTENT_EVIDENCE_REBOUND";
    static final String PARTIAL_FALLBACK_WARNING = "MODEL_PARTIAL_GROUNDED_FALLBACK";
    private static final Set<GeneratedContentType> RESUME_TYPES = Set.of(
            GeneratedContentType.RESUME_HEADLINE,
            GeneratedContentType.PROFESSIONAL_SUMMARY,
            GeneratedContentType.SKILL_SECTION,
            GeneratedContentType.EXPERIENCE_BULLET,
            GeneratedContentType.PROJECT_BULLET,
            GeneratedContentType.EDUCATION_SECTION);
    private static final Set<GeneratedContentType> SINGLE_FACT_TYPES = Set.of(
            GeneratedContentType.EXPERIENCE_BULLET,
            GeneratedContentType.PROJECT_BULLET,
            GeneratedContentType.EDUCATION_SECTION);

    private final GeneratedContentValidator validator;

    EvidenceBoundContentMerger(GeneratedContentValidator validator) {
        this.validator = validator;
    }

    GeneratedApplicationContent merge(
            GeneratedApplicationContent generated,
            ApplicationContentGenerationRequest request,
            TailoringPlan plan,
            JobClaimEvidence jobEvidence) {
        GeneratedApplicationContent fallback = OllamaApplicationContentGenerator.groundRepair(
                generated == null
                        ? new GeneratedApplicationContent(List.of(), List.of(), List.of(), List.of())
                        : generated,
                request,
                plan);
        if (generated == null || generated.contents() == null || generated.claims() == null) return fallback;

        Set<UUID> allowedFacts = request.facts().stream()
                .map(VerifiedFactSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, List<GeneratedClaimAtom>> claimsByKey = new LinkedHashMap<>();
        generated.claims().stream().filter(Objects::nonNull).forEach(claim ->
                claimsByKey.computeIfAbsent(claim.contentKey(), ignored -> new ArrayList<>()).add(claim));

        var accepted = new ArrayList<AcceptedSection>();
        for (GeneratedContentItem content : generated.contents()) {
            if (content == null || content.type() == null || content.key() == null) continue;
            List<GeneratedClaimAtom> originalClaims = claimsByKey.getOrDefault(content.key(), List.of());
            List<GeneratedClaimAtom> candidateClaims = RESUME_TYPES.contains(content.type())
                    ? reboundCandidateClaim(content, originalClaims, allowedFacts)
                    : originalClaims;
            if (candidateClaims.isEmpty()) continue;
            var isolated = new GeneratedApplicationContent(
                    List.of(content), candidateClaims, List.of(), List.of());
            if (validator.validate(isolated, request.facts(), jobEvidence).valid()) {
                accepted.add(new AcceptedSection(content, candidateClaims));
            }
        }

        long acceptedResumeSections = accepted.stream()
                .filter(section -> RESUME_TYPES.contains(section.content().type()))
                .count();
        if (acceptedResumeSections == 0) return fallback;

        var merged = fallbackSections(fallback);
        for (AcceptedSection section : accepted) replace(merged, section);
        var ordered = merged.values().stream()
                .sorted(Comparator.comparingInt(section -> section.content().order()))
                .toList();
        var contents = new ArrayList<GeneratedContentItem>();
        var claims = new ArrayList<GeneratedClaimAtom>();
        for (int index = 0; index < ordered.size(); index++) {
            AcceptedSection section = ordered.get(index);
            GeneratedContentItem content = section.content();
            contents.add(new GeneratedContentItem(content.type(), content.key(), index, content.text()));
            claims.addAll(section.claims());
        }
        var warnings = new LinkedHashSet<String>();
        if (generated.warnings() != null) warnings.addAll(generated.warnings());
        warnings.remove("MODEL_GROUNDED_FALLBACK");
        warnings.remove("MODEL_OUTPUT_REPLACED_WITH_EVIDENCE_GROUNDED_DRAFT");
        warnings.add(REBOUND_WARNING);
        if (accepted.size() < generated.contents().size()) warnings.add(PARTIAL_FALLBACK_WARNING);
        var result = new GeneratedApplicationContent(
                List.copyOf(contents), List.copyOf(claims), List.copyOf(warnings),
                generated.unsupportedRequirements() == null ? List.of() : generated.unsupportedRequirements());
        return validator.validate(result, request.facts(), jobEvidence).valid() ? result : fallback;
    }

    private static List<GeneratedClaimAtom> reboundCandidateClaim(
            GeneratedContentItem content,
            List<GeneratedClaimAtom> claims,
            Set<UUID> allowedFacts) {
        List<UUID> factIds = claims.stream()
                .filter(Objects::nonNull)
                .filter(claim -> claim.factIds() != null)
                .flatMap(claim -> claim.factIds().stream())
                .filter(Objects::nonNull)
                .filter(allowedFacts::contains)
                .distinct()
                .toList();
        if (factIds.isEmpty() || (SINGLE_FACT_TYPES.contains(content.type()) && factIds.size() != 1)) {
            return List.of();
        }
        return List.of(new GeneratedClaimAtom(
                content.key(), content.text(), ClaimType.CANDIDATE_FACT, factIds, List.of(), null));
    }

    private static Map<String, AcceptedSection> fallbackSections(GeneratedApplicationContent fallback) {
        Map<String, List<GeneratedClaimAtom>> claimsByKey = new LinkedHashMap<>();
        fallback.claims().forEach(claim ->
                claimsByKey.computeIfAbsent(claim.contentKey(), ignored -> new ArrayList<>()).add(claim));
        var result = new LinkedHashMap<String, AcceptedSection>();
        fallback.contents().forEach(content -> result.put(
                identity(content, claimsByKey.getOrDefault(content.key(), List.of())),
                new AcceptedSection(content, claimsByKey.getOrDefault(content.key(), List.of()))));
        return result;
    }

    private static void replace(Map<String, AcceptedSection> sections, AcceptedSection replacement) {
        String replacementIdentity = identity(replacement.content(), replacement.claims());
        if (replacementIdentity.startsWith("fact:")) {
            sections.remove(replacementIdentity);
        } else {
            sections.entrySet().removeIf(entry -> entry.getValue().content().type() == replacement.content().type());
        }
        sections.put(replacementIdentity, replacement);
    }

    private static String identity(GeneratedContentItem content, List<GeneratedClaimAtom> claims) {
        if (SINGLE_FACT_TYPES.contains(content.type())) {
            List<UUID> ids = claims.stream().filter(Objects::nonNull)
                    .filter(claim -> claim.factIds() != null)
                    .flatMap(claim -> claim.factIds().stream()).filter(Objects::nonNull).distinct().toList();
            if (ids.size() == 1) return "fact:" + ids.getFirst();
        }
        return "type:" + content.type();
    }

    private record AcceptedSection(GeneratedContentItem content, List<GeneratedClaimAtom> claims) {}
}
