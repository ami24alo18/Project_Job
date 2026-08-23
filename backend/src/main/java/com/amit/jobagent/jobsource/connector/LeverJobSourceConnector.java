package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.common.config.JobIngestionProperties;
import com.amit.jobagent.job.JobSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Read-only adapter for Lever's documented public Postings API V0. */
@Component
public final class LeverJobSourceConnector implements JobSourceConnector {
    private final ObjectMapper mapper;
    private final ProviderEndpointResolver endpoints;
    private final AuthorizedFeedHttpClient http;

    @Autowired
    public LeverJobSourceConnector(ObjectMapper mapper, JobIngestionProperties properties) {
        this(mapper, new ProviderEndpointResolver(),
                new AuthorizedFeedHttpClient(ConnectorSupport.settings(properties)));
    }

    LeverJobSourceConnector(
            ObjectMapper mapper,
            ProviderEndpointResolver endpoints,
            AuthorizedFeedHttpClient http) {
        this.mapper = mapper;
        this.endpoints = endpoints;
        this.http = http;
    }

    @Override
    public JobSourceType supportedType() {
        return JobSourceType.LEVER;
    }

    @Override
    public SourceFetchResult fetch(SourceFetchRequest request) {
        requireSupported(request);
        int skip = checkpointOffset(request.checkpoint());
        int pagesFetched = 0;
        var records = new ArrayList<RawJobRecord>();
        var errors = new ArrayList<SourceRecordError>();

        for (int page = 0; page < request.maximumPages(); page++) {
            byte[] body = http.get(endpoints.leverPostings(
                    request.providerIdentifier(), request.region(), skip, request.pageSize()));
            JsonNode jobs = parseTopLevel(body);
            pagesFetched++;
            int returned = jobs.size();
            for (int index = 0; index < returned; index++) {
                JsonNode item = jobs.get(index);
                RawJobRecord record = map(item);
                if (record == null) {
                    errors.add(ConnectorSupport.malformed(skip + index, item));
                } else {
                    records.add(record);
                }
            }
            skip += returned;
            if (returned < request.pageSize()) {
                return new SourceFetchResult(supportedType(), records, errors, pagesFetched, true,
                        new SourceCheckpoint(Integer.toString(skip)));
            }
        }
        return new SourceFetchResult(supportedType(), records, errors, pagesFetched, false,
                new SourceCheckpoint(Integer.toString(skip)));
    }

    private JsonNode parseTopLevel(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isArray()) {
                throw invalidPayload("Lever returned an invalid top-level payload", null);
            }
            return root;
        } catch (IOException ex) {
            throw invalidPayload("Lever returned malformed JSON", ex);
        }
    }

    private static RawJobRecord map(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String id = ConnectorSupport.text(item, "id");
        String title = ConnectorSupport.text(item, "text");
        if (id == null || title == null) {
            return null;
        }
        JsonNode categories = item.get("categories");
        JsonNode salary = item.get("salaryRange");
        var description = description(item);
        return new RawJobRecord(
                id,
                null,
                title,
                ConnectorSupport.text(categories, "location"),
                ConnectorSupport.text(item, "country"),
                ConnectorSupport.text(item, "workplaceType"),
                ConnectorSupport.text(categories, "commitment"),
                ConnectorSupport.text(categories, "department"),
                ConnectorSupport.text(categories, "team"),
                description.plainText(),
                description.html(),
                ConnectorSupport.text(item, "applyUrl"),
                ConnectorSupport.text(item, "hostedUrl"),
                ConnectorSupport.decimal(salary, "min"),
                ConnectorSupport.decimal(salary, "max"),
                ConnectorSupport.text(salary, "currency"),
                ConnectorSupport.text(salary, "interval"),
                null,
                null,
                null);
    }

    private static Description description(JsonNode item) {
        String plain = ConnectorSupport.text(item, "descriptionPlain");
        String html = ConnectorSupport.text(item, "description");
        String additionalPlain = ConnectorSupport.text(item, "additionalPlain");
        String additionalHtml = ConnectorSupport.text(item, "additional");
        JsonNode lists = item.get("lists");
        boolean hasLists = lists != null && lists.isArray() && !lists.isEmpty();
        boolean hasAdditional = additionalPlain != null || additionalHtml != null;
        if (!hasLists && !hasAdditional) return new Description(plain, html);

        var parts = new ArrayList<String>();
        appendHtmlOrEscapedText(parts, html, plain, "p");
        if (hasLists) for (JsonNode list : lists) {
            if (list == null || !list.isObject()) continue;
            String heading = ConnectorSupport.text(list, "text");
            if (heading != null) parts.add(new Element("h3").text(heading).outerHtml());
            String content = ConnectorSupport.text(list, "content");
            if (content != null) parts.add(content);
        }
        appendHtmlOrEscapedText(parts, additionalHtml, additionalPlain, "p");
        return new Description(null, parts.isEmpty() ? null : String.join("\n", parts));
    }

    private static void appendHtmlOrEscapedText(List<String> parts, String html, String plain, String tag) {
        if (html != null) parts.add(html);
        else if (plain != null) parts.add(new Element(tag).text(plain).outerHtml());
    }

    private record Description(String plainText, String html) {}

    private static int checkpointOffset(SourceCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.value() == null) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(checkpoint.value());
            if (offset < 0) {
                throw new NumberFormatException("negative");
            }
            return offset;
        } catch (NumberFormatException ex) {
            throw new SourceFetchException(SourceFetchErrorCode.INVALID_CONFIGURATION,
                    "Lever checkpoint must contain a non-negative offset", null, false, null, ex);
        }
    }

    private static void requireSupported(SourceFetchRequest request) {
        if (request.sourceType() != JobSourceType.LEVER) {
            throw new SourceFetchException(SourceFetchErrorCode.UNSUPPORTED_SOURCE,
                    "The Lever connector cannot fetch this source type");
        }
    }

    private static SourceFetchException invalidPayload(String message, Throwable cause) {
        return new SourceFetchException(
                SourceFetchErrorCode.INVALID_PAYLOAD, message, 200, false, null, cause);
    }
}
