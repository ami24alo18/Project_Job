package com.amit.jobagent.jobsource;

import com.amit.jobagent.common.error.DomainValidationException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Deployment-owned allowlist metadata. Recipe implementation is never accepted through the API. */
@Component
class ExtractionRecipeRegistry {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");
    private final boolean enabled;
    private final Map<String, ReviewedRecipe> recipes;

    ExtractionRecipeRegistry(ExtractionRecipeProperties properties) {
        enabled = properties.enabled();
        var configured = new HashMap<String, ReviewedRecipe>();
        for (var definition : properties.definitions()) {
            String id = safe(definition.id(), "recipe id");
            String version = safe(definition.version(), "recipe version");
            String host = definition.canonicalHost() == null ? "" : definition.canonicalHost().trim().toLowerCase(Locale.ROOT);
            if (host.isBlank() || host.contains(":") || host.contains("/") || host.length() > 253) {
                throw new IllegalStateException("Extraction recipe canonical host is invalid");
            }
            var prefixes = definition.allowedPathPrefixes().stream().map(prefix -> {
                String value = prefix == null ? "" : prefix.trim();
                if (!value.startsWith("/") || value.contains("\\") || value.contains("..")) {
                    throw new IllegalStateException("Extraction recipe path prefix is invalid");
                }
                return value;
            }).distinct().toList();
            if (prefixes.isEmpty()) throw new IllegalStateException("Extraction recipe needs an allowed path prefix");
            if (configured.putIfAbsent(id, new ReviewedRecipe(id, version, host, prefixes, definition.enabled())) != null) {
                throw new IllegalStateException("Duplicate extraction recipe id");
            }
        }
        recipes = Map.copyOf(configured);
    }

    ReviewedRecipe requireCompatible(String recipeId, String canonicalHost, String careerSiteUrl) {
        if (!enabled) throw new DomainValidationException("Reviewed extraction recipes are disabled");
        ReviewedRecipe recipe = recipes.get(recipeId == null ? "" : recipeId.trim());
        if (recipe == null || !recipe.enabled()) throw new DomainValidationException("The extraction recipe is not available");
        URI uri;
        try { uri = URI.create(careerSiteUrl); }
        catch (IllegalArgumentException invalid) { throw new DomainValidationException("The career-site identity is invalid"); }
        String host = canonicalHost == null ? "" : canonicalHost.toLowerCase(Locale.ROOT);
        String uriHost = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !recipe.canonicalHost().equals(host)
                || !recipe.canonicalHost().equals(uriHost)
                || recipe.allowedPathPrefixes().stream().noneMatch(prefix -> pathMatchesPrefix(path, prefix))) {
            throw new DomainValidationException("The extraction recipe is not approved for this career site");
        }
        return recipe;
    }

    private static boolean pathMatchesPrefix(String path, String prefix) {
        if ("/".equals(prefix)) return true;
        String normalizedPrefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        return path.equals(normalizedPrefix) || path.startsWith(normalizedPrefix + "/");
    }

    private static String safe(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (!SAFE_ID.matcher(normalized).matches()) throw new IllegalStateException("Extraction " + field + " is invalid");
        return normalized;
    }

    record ReviewedRecipe(String id, String version, String canonicalHost,
            java.util.List<String> allowedPathPrefixes, boolean enabled) {}
}
