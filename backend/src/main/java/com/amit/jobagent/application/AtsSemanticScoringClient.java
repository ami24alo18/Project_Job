package com.amit.jobagent.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class AtsSemanticScoringClient implements SemanticResumeMatchProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AtsSemanticScoringClient.class);
    private static final int MAX_CACHE_ENTRIES = 500;
    private final AtsScoringProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final Map<String, Scores> cache = new ConcurrentHashMap<>();

    @Autowired
    AtsSemanticScoringClient(AtsScoringProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
    }

    AtsSemanticScoringClient(AtsScoringProperties properties, ObjectMapper mapper, HttpClient client) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = client;
    }

    @Override
    public Optional<Scores> score(String jobDescription, String currentResume, String generatedResume) {
        if (!properties.enabled()) {
            LOG.debug("ATS semantic scorer is disabled; using keyword fallback");
            return Optional.empty();
        }
        String job = bounded(jobDescription);
        String current = bounded(currentResume);
        String generated = bounded(generatedResume);
        if (job.isBlank() || current.isBlank() || generated.isBlank()) {
            LOG.warn("ATS semantic scoring skipped because a document is blank (jd={}, current={}, generated={})",
                    job.length(), current.length(), generated.length());
            return Optional.empty();
        }
        String cacheKey = digest(job + "\u0000" + current + "\u0000" + generated);
        Scores cached = cache.get(cacheKey);
        if (cached != null) return Optional.of(cached);
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of(
                    "jobDescription", job,
                    "documents", new Object[] {
                            Map.of("id", "current", "text", current),
                            Map.of("id", "generated", "text", generated)
                    }));
            var builder = HttpRequest.newBuilder(URI.create(stripTrailingSlash(properties.baseUrl()) + "/v1/score"))
                    .timeout(properties.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (!properties.token().isBlank()) builder.header("X-ATS-Scoring-Token", properties.token());
            HttpResponse<String> response = client.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                LOG.warn("ATS semantic scorer returned HTTP {}; using keyword fallback", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(response.body());
            Integer currentScore = null;
            Integer generatedScore = null;
            for (JsonNode result : root.path("scores")) {
                int value = Math.clamp((int) Math.round(result.path("score").asDouble()), 0, 100);
                if ("current".equals(result.path("id").asText())) currentScore = value;
                if ("generated".equals(result.path("id").asText())) generatedScore = value;
            }
            if (currentScore == null || generatedScore == null) return Optional.empty();
            Scores scores = new Scores(currentScore, generatedScore,
                    root.path("method").asText("NBK_ATS_SEMANTIC_V1_EN_COSINE"));
            if (cache.size() >= MAX_CACHE_ENTRIES) cache.clear();
            cache.put(cacheKey, scores);
            return Optional.of(scores);
        } catch (Exception failure) {
            LOG.warn("ATS semantic scorer unavailable; using keyword fallback: {}", failure.toString());
            return Optional.empty();
        }
    }

    private String bounded(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= properties.maxDocumentCharacters()
                ? text : text.substring(0, properties.maxDocumentCharacters());
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }
}
