package com.amit.jobagent.jobsource.connector;

import com.amit.jobagent.common.config.JobIngestionProperties;
import com.amit.jobagent.job.JobSourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Read-only adapter for Greenhouse's public Job Board list API. */
@Component
public final class GreenhouseJobSourceConnector implements JobSourceConnector {
    private final ObjectMapper mapper;
    private final ProviderEndpointResolver endpoints;
    private final AuthorizedFeedHttpClient http;

    @Autowired
    public GreenhouseJobSourceConnector(ObjectMapper mapper, JobIngestionProperties properties) {
        this(mapper, new ProviderEndpointResolver(),
                new AuthorizedFeedHttpClient(ConnectorSupport.settings(properties)));
    }

    GreenhouseJobSourceConnector(
            ObjectMapper mapper,
            ProviderEndpointResolver endpoints,
            AuthorizedFeedHttpClient http) {
        this.mapper = mapper;
        this.endpoints = endpoints;
        this.http = http;
    }

    @Override
    public JobSourceType supportedType() {
        return JobSourceType.GREENHOUSE;
    }

    @Override
    public SourceFetchResult fetch(SourceFetchRequest request) {
        requireSupported(request);
        if (request.checkpoint() != null && request.checkpoint().value() != null) {
            throw new SourceFetchException(SourceFetchErrorCode.INVALID_CONFIGURATION,
                    "Greenhouse job-board lists are unpaged and do not accept a checkpoint");
        }
        byte[] body = http.get(endpoints.greenhouseJobs(request.providerIdentifier(), request.region()));
        JsonNode jobs = parseJobs(body);
        var records = new ArrayList<RawJobRecord>();
        var errors = new ArrayList<SourceRecordError>();
        for (int index = 0; index < jobs.size(); index++) {
            JsonNode item = jobs.get(index);
            RawJobRecord record = map(item);
            if (record == null) {
                errors.add(ConnectorSupport.malformed(index, item));
            } else {
                records.add(record);
            }
        }
        return new SourceFetchResult(
                supportedType(), records, errors, 1, true, SourceCheckpoint.beginning());
    }

    private JsonNode parseJobs(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode jobs = root == null ? null : root.get("jobs");
            if (root == null || !root.isObject() || jobs == null || !jobs.isArray()) {
                throw invalidPayload("Greenhouse returned an invalid top-level payload", null);
            }
            return jobs;
        } catch (IOException ex) {
            throw invalidPayload("Greenhouse returned malformed JSON", ex);
        }
    }

    private static RawJobRecord map(JsonNode item) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String id = ConnectorSupport.text(item, "id");
        String title = ConnectorSupport.text(item, "title");
        if (id == null || title == null) {
            return null;
        }
        JsonNode location = item.get("location");
        return new RawJobRecord(
                id,
                ConnectorSupport.text(item, "company_name"),
                title,
                ConnectorSupport.text(location, "name"),
                null,
                null,
                null,
                ConnectorSupport.firstName(item.get("departments")),
                null,
                null,
                ConnectorSupport.text(item, "content"),
                ConnectorSupport.text(item, "absolute_url"),
                ConnectorSupport.text(item, "absolute_url"),
                null,
                null,
                null,
                null,
                ConnectorSupport.instant(item, "first_published"),
                ConnectorSupport.instant(item, "updated_at"),
                ConnectorSupport.instant(item, "application_deadline"));
    }

    private static void requireSupported(SourceFetchRequest request) {
        if (request.sourceType() != JobSourceType.GREENHOUSE) {
            throw new SourceFetchException(SourceFetchErrorCode.UNSUPPORTED_SOURCE,
                    "The Greenhouse connector cannot fetch this source type");
        }
    }

    private static SourceFetchException invalidPayload(String message, Throwable cause) {
        return new SourceFetchException(
                SourceFetchErrorCode.INVALID_PAYLOAD, message, 200, false, null, cause);
    }
}
