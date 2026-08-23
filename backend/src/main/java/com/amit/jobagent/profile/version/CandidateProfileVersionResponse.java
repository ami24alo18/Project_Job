package com.amit.jobagent.profile.version;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
public record CandidateProfileVersionResponse(UUID id,int versionNumber,JsonNode snapshot,String checksum,String changeReason,boolean active,Instant createdAt,boolean unchanged){}
