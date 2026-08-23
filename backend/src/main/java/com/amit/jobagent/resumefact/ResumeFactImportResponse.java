package com.amit.jobagent.resumefact;
import java.util.List;
import java.util.UUID;
public record ResumeFactImportResponse(List<UUID> importedIds,List<Integer> duplicateIndexes,int requestedCount,int importedCount) {}
