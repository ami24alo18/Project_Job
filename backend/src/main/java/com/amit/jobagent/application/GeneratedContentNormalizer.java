package com.amit.jobagent.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Repairs referential mistakes that JSON Schema cannot express, without changing model-authored
 * prose or manufacturing evidence. Unsupported prose remains subject to fail-closed validation.
 */
final class GeneratedContentNormalizer {
    private static final Pattern TOKEN = Pattern.compile(
            "[\\p{L}\\p{N}+#]+(?:[./'-][\\p{L}\\p{N}+#]+)*");
    static final String WARNING = "MODEL_OUTPUT_STRUCTURE_NORMALIZED";

    private GeneratedContentNormalizer() {}

    static GeneratedApplicationContent normalize(GeneratedApplicationContent output) {
        if (output == null || output.contents() == null || output.claims() == null
                || output.warnings() == null || output.unsupportedRequirements() == null) return output;

        var occurrences = new HashMap<String, Integer>();
        var usedKeys = new LinkedHashSet<String>();
        var entries = new ArrayList<ContentEntry>();
        boolean changed = false;
        for (GeneratedContentItem content : output.contents()) {
            if (content == null || content.key() == null || content.key().isBlank()) {
                entries.add(new ContentEntry(content == null ? null : content.key(), content));
                continue;
            }
            int occurrence = occurrences.merge(content.key(), 1, Integer::sum);
            String key = occurrence == 1 ? content.key() : uniqueKey(content.key(), occurrence, usedKeys);
            usedKeys.add(key);
            if (!key.equals(content.key())) changed = true;
            entries.add(new ContentEntry(content.key(), new GeneratedContentItem(
                    content.type(), key, content.order(), content.text())));
        }

        var normalizedClaims = new ArrayList<GeneratedClaimAtom>();
        Set<String> seen = new LinkedHashSet<>();
        for (GeneratedClaimAtom claim : output.claims()) {
            if (claim == null || claim.contentKey() == null || claim.claimText() == null) {
                normalizedClaims.add(claim);
                continue;
            }
            List<ContentEntry> sameOriginalKey = entries.stream()
                    .filter(entry -> Objects.equals(entry.originalKey(), claim.contentKey()))
                    .toList();
            List<ContentEntry> matching = sameOriginalKey.stream()
                    .filter(entry -> containsClaim(entry.content(), claim.claimText()))
                    .toList();
            if (matching.isEmpty() && sameOriginalKey.size() == 1) matching = sameOriginalKey;
            if (matching.isEmpty()) {
                matching = entries.stream()
                        .filter(entry -> containsClaim(entry.content(), claim.claimText()))
                        .toList();
            }
            if (matching.isEmpty()) {
                changed = true;
                continue;
            }
            if (matching.size() != 1 || !Objects.equals(matching.get(0).originalKey(), claim.contentKey())
                    || !Objects.equals(matching.get(0).content().key(), claim.contentKey())) changed = true;
            for (ContentEntry entry : matching) {
                if (entry.content() == null || entry.content().key() == null) continue;
                var normalized = new GeneratedClaimAtom(
                        entry.content().key(), claim.claimText(), claim.claimType(),
                        claim.factIds(), claim.requirementIds(), claim.jobFieldReference());
                String identity = canonical(normalized.contentKey()) + "|" + canonical(normalized.claimText());
                if (seen.add(identity)) normalizedClaims.add(normalized);
                else changed = true;
            }
        }

        Set<String> claimedKeys = normalizedClaims.stream()
                .filter(Objects::nonNull)
                .map(GeneratedClaimAtom::contentKey)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var contents = entries.stream()
                .map(ContentEntry::content)
                .filter(Objects::nonNull)
                .filter(content -> content.key() == null || claimedKeys.contains(content.key()))
                .toList();
        if (contents.size() != entries.size()) changed = true;

        boolean duplicateOrders = contents.stream().map(GeneratedContentItem::order).distinct().count() != contents.size();
        if (duplicateOrders) changed = true;
        var orderedContents = new ArrayList<GeneratedContentItem>();
        for (int index = 0; index < contents.size(); index++) {
            GeneratedContentItem content = contents.get(index);
            orderedContents.add(duplicateOrders
                    ? new GeneratedContentItem(content.type(), content.key(), index, content.text())
                    : content);
        }

        var warnings = new ArrayList<>(output.warnings());
        if (changed && !warnings.contains(WARNING)) warnings.add(WARNING);
        return new GeneratedApplicationContent(
                List.copyOf(orderedContents), List.copyOf(normalizedClaims),
                List.copyOf(warnings), output.unsupportedRequirements());
    }

    private static boolean containsClaim(GeneratedContentItem content, String claimText) {
        return content != null && content.text() != null && !canonical(claimText).isBlank()
                && (" " + canonical(content.text()) + " ").contains(" " + canonical(claimText) + " ");
    }

    private static String uniqueKey(String original, int occurrence, Set<String> used) {
        int counter = occurrence;
        String candidate;
        do {
            String suffix = "-" + counter++;
            String base = original.substring(0, Math.min(original.length(), 150 - suffix.length()));
            candidate = base + suffix;
        } while (used.contains(candidate));
        return candidate;
    }

    private static String canonical(String value) {
        if (value == null) return "";
        var matcher = TOKEN.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT));
        var tokens = new ArrayList<String>();
        while (matcher.find()) tokens.add(matcher.group());
        return String.join(" ", tokens);
    }

    private record ContentEntry(String originalKey, GeneratedContentItem content) {}
}
