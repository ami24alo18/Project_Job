package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.JobSourceConnectorType;
import com.amit.jobagent.jobsource.discovery.CareerSitePageFetcher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** Extracts bounded schema.org JobPosting data from one guarded public page. */
@Component
public final class GenericJsonLdJobSourceConnector implements JobSourceConnector {
    private static final int MAXIMUM_JSON_LD_BLOCKS = 50;
    private static final int MAXIMUM_JOB_NODES = 25;
    private final ObjectMapper mapper;
    private final CareerSitePageFetcher pages;

    public GenericJsonLdJobSourceConnector(ObjectMapper mapper, CareerSitePageFetcher pages) {
        this.mapper = mapper;
        this.pages = pages;
    }

    @Override public JobSourceType supportedType() { return JobSourceType.CAREER_SITE; }
    @Override public JobSourceConnectorType supportedConnector() { return JobSourceConnectorType.GENERIC_JSON_LD; }

    @Override
    public SourceFetchResult fetch(SourceFetchRequest request) {
        if (request.sourceType() != JobSourceType.CAREER_SITE
                || request.connectorType() != JobSourceConnectorType.GENERIC_JSON_LD
                || request.careerSiteUrl() == null) {
            throw new SourceFetchException(SourceFetchErrorCode.INVALID_CONFIGURATION,
                    "The JSON-LD connector requires a discovered career-site page");
        }
        if (request.checkpoint().value() != null) {
            throw new SourceFetchException(SourceFetchErrorCode.INVALID_CONFIGURATION,
                    "Single-page JSON-LD sources do not accept checkpoints");
        }
        final var page = fetch(request.careerSiteUrl());
        var document = Jsoup.parse(page.markup(), page.canonicalUrl());
        var scripts = document.select("script[type=application/ld+json]");
        if (scripts.size() > MAXIMUM_JSON_LD_BLOCKS) throw invalid("The page contains too many JSON-LD blocks", null);
        var records = new ArrayList<RawJobRecord>();
        var errors = new ArrayList<SourceRecordError>();
        for (var script : scripts) {
            String value = script.data().isBlank() ? script.html() : script.data();
            try { collect(mapper.readTree(value), page.canonicalUrl(), records); }
            catch (JsonProcessingException ex) {
                errors.add(new SourceRecordError(errors.size(), null, "MALFORMED_JSON_LD",
                        "A structured-data block could not be parsed"));
            }
            if (records.size() > MAXIMUM_JOB_NODES) throw invalid("The page contains too many JobPosting records", null);
        }
        if (records.isEmpty() && errors.isEmpty()) throw invalid("The page no longer contains JobPosting structured data", null);
        return new SourceFetchResult(supportedType(), records, errors, 1, true, SourceCheckpoint.beginning());
    }

    private com.amit.jobagent.jobsource.discovery.CareerSitePage fetch(String url) {
        try { return pages.fetch(url); }
        catch (CareerSiteDiscoveryException ex) {
            throw new SourceFetchException(SourceFetchErrorCode.IO_ERROR,
                    "The guarded career-site fetch failed", null, false, null, ex);
        } catch (IllegalArgumentException ex) {
            throw new SourceFetchException(SourceFetchErrorCode.INVALID_CONFIGURATION,
                    "The career-site URL is invalid", null, false, null, ex);
        }
    }

    private void collect(JsonNode node, String pageUrl, ArrayList<RawJobRecord> records) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) { node.forEach(item -> collect(item, pageUrl, records)); return; }
        if (!node.isObject()) return;
        if (isJobPosting(node)) records.add(map(node, pageUrl));
        JsonNode graph = node.get("@graph");
        if (graph != null) collect(graph, pageUrl, records);
    }

    private static boolean isJobPosting(JsonNode node) {
        JsonNode type = node.get("@type");
        if (type == null) return false;
        if (type.isTextual()) return "JobPosting".equalsIgnoreCase(type.asText());
        if (type.isArray()) for (JsonNode value : type) if (value.isTextual()
                && "JobPosting".equalsIgnoreCase(value.asText())) return true;
        return false;
    }

    private static RawJobRecord map(JsonNode node, String pageUrl) {
        String title = text(node, "title");
        if (title == null) throw invalid("A JobPosting record is missing its title", null);
        String sourceUrl = absolute(text(node, "url"), pageUrl);
        String id = identifier(node);
        if (id == null) id = sourceUrl == null ? sha256(pageUrl + "\n" + title) : sourceUrl;
        JsonNode organization = node.get("hiringOrganization");
        JsonNode location = node.get("jobLocation");
        if (location != null && location.isArray()) location = location.isEmpty() ? null : location.get(0);
        JsonNode address = location == null ? null : location.get("address");
        String locality = text(address, "addressLocality");
        String region = text(address, "addressRegion");
        String country = country(address == null ? null : address.get("addressCountry"));
        String place = join(locality, region, country);
        String description = text(node, "description");
        return new RawJobRecord(id, text(organization, "name"), title, place, country,
                text(node, "jobLocationType"), text(node, "employmentType"),
                text(node, "occupationalCategory"), null, null, description,
                sourceUrl == null ? pageUrl : sourceUrl, sourceUrl == null ? pageUrl : sourceUrl,
                null, null, null, null, ConnectorSupport.instant(node, "datePosted"),
                null, ConnectorSupport.instant(node, "validThrough"));
    }

    private static String identifier(JsonNode node) {
        JsonNode value = node.get("identifier");
        if (value == null) return null;
        if (value.isTextual()) return blank(value.asText());
        return text(value, "value");
    }
    private static String text(JsonNode node, String field) { return blank(ConnectorSupport.text(node, field)); }
    private static String country(JsonNode node) {
        if (node == null) return null;
        return node.isTextual() ? blank(node.asText()) : text(node, "name");
    }
    private static String absolute(String raw, String base) {
        if (raw == null) return null;
        try { URI resolved = URI.create(base).resolve(raw); return "https".equalsIgnoreCase(resolved.getScheme()) ? resolved.toASCIIString() : null; }
        catch (IllegalArgumentException ex) { return null; }
    }
    private static String join(String... values) {
        var joiner = new java.util.StringJoiner(", ");
        for (String value : values) if (value != null) joiner.add(value);
        return joiner.length() == 0 ? null : joiner.toString();
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static SourceFetchException invalid(String message, Throwable cause) {
        return new SourceFetchException(SourceFetchErrorCode.INVALID_PAYLOAD, message, 200, false, null, cause);
    }
}
