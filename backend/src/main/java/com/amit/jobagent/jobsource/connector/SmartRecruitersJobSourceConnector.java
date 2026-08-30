package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.common.config.JobIngestionProperties;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.JobSourceConnectorType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Bounded adapter for SmartRecruiters' public Posting API. */
@Component
public final class SmartRecruitersJobSourceConnector implements JobSourceConnector {
    private final ObjectMapper mapper;
    private final ProviderEndpointResolver endpoints;
    private final AuthorizedFeedHttpClient http;

    @Autowired
    public SmartRecruitersJobSourceConnector(ObjectMapper mapper, JobIngestionProperties properties) {
        this(mapper, new ProviderEndpointResolver(),
                new AuthorizedFeedHttpClient(ConnectorSupport.settings(properties)));
    }

    SmartRecruitersJobSourceConnector(ObjectMapper mapper, ProviderEndpointResolver endpoints,
            AuthorizedFeedHttpClient http) {
        this.mapper = mapper;
        this.endpoints = endpoints;
        this.http = http;
    }

    @Override public JobSourceType supportedType() { return JobSourceType.CAREER_SITE; }
    @Override public JobSourceConnectorType supportedConnector() { return JobSourceConnectorType.SMARTRECRUITERS; }

    @Override
    public SourceFetchResult fetch(SourceFetchRequest request) {
        if (request.sourceType() != JobSourceType.CAREER_SITE
                || request.connectorType() != JobSourceConnectorType.SMARTRECRUITERS) {
            throw new SourceFetchException(SourceFetchErrorCode.UNSUPPORTED_SOURCE,
                    "The SmartRecruiters connector cannot fetch this source");
        }
        if (request.checkpoint().value() != null) {
            throw new SourceFetchException(SourceFetchErrorCode.INVALID_CONFIGURATION,
                    "SmartRecruiters inventory scans restart from the first page");
        }
        var records = new ArrayList<RawJobRecord>();
        var errors = new ArrayList<SourceRecordError>();
        int offset = 0;
        int pages = 0;
        int total = Integer.MAX_VALUE;
        while (pages < request.maximumPages() && offset < total) {
            JsonNode root = parse(http.get(endpoints.smartRecruitersPostings(
                    request.providerIdentifier(), offset, request.pageSize())));
            JsonNode content = root.get("content");
            if (!root.isObject() || content == null || !content.isArray()
                    || !root.path("totalFound").canConvertToInt()) {
                throw invalid("SmartRecruiters returned an invalid list payload", null);
            }
            total = Math.max(0, root.path("totalFound").asInt());
            for (int index = 0; index < content.size(); index++) {
                RawJobRecord record = map(content.get(index), request.providerIdentifier());
                if (record == null) errors.add(ConnectorSupport.malformed(offset + index, content.get(index)));
                else records.add(record);
            }
            pages++;
            if (content.isEmpty()) { offset = total; break; }
            offset += content.size();
        }
        boolean complete = offset >= total;
        return new SourceFetchResult(supportedType(), records, errors, pages, complete,
                complete ? SourceCheckpoint.beginning() : new SourceCheckpoint(Integer.toString(offset)));
    }

    private JsonNode parse(byte[] body) {
        try { return mapper.readTree(body); }
        catch (IOException ex) { throw invalid("SmartRecruiters returned malformed JSON", ex); }
    }

    private static RawJobRecord map(JsonNode item, String identifier) {
        if (item == null || !item.isObject()) return null;
        String id = ConnectorSupport.text(item, "id");
        String title = ConnectorSupport.text(item, "name");
        if (id == null || title == null) return null;
        JsonNode location = item.get("location");
        StringJoiner place = new StringJoiner(", ");
        add(place, ConnectorSupport.text(location, "city"));
        add(place, ConnectorSupport.text(location, "region"));
        add(place, ConnectorSupport.text(location, "country"));
        String postingUrl = "https://jobs.smartrecruiters.com/" + identifier + "/" + id;
        return new RawJobRecord(id, ConnectorSupport.text(item.get("company"), "name"), title,
                place.length() == 0 ? null : place.toString(), ConnectorSupport.text(location, "country"),
                location != null && location.path("remote").asBoolean(false) ? "REMOTE" : null,
                ConnectorSupport.text(item.get("typeOfEmployment"), "label"),
                ConnectorSupport.text(item.get("department"), "label"),
                ConnectorSupport.text(item.get("function"), "label"), null, null,
                postingUrl, postingUrl, null, null, null, null,
                ConnectorSupport.instant(item, "releasedDate"), null, null);
    }

    private static void add(StringJoiner joiner, String value) { if (value != null) joiner.add(value); }
    private static SourceFetchException invalid(String message, Throwable cause) {
        return new SourceFetchException(SourceFetchErrorCode.INVALID_PAYLOAD, message, 200, false, null, cause);
    }
}
