package com.amit.jobagent.jobsource;

import java.util.UUID;

record ExternalIngestionStart(UUID eventDatabaseId, UUID runId) {}
