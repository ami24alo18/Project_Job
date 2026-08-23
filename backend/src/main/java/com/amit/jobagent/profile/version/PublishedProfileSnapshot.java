package com.amit.jobagent.profile.version;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record PublishedProfileSnapshot(UUID id, UUID profileId, String checksum, JsonNode snapshot) {}
