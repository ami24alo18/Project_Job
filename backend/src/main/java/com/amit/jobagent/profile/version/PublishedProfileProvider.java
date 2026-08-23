package com.amit.jobagent.profile.version;

import com.amit.jobagent.common.error.ResourceNotFoundException;
import com.amit.jobagent.profile.ActiveProfileProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PublishedProfileProvider {
    private final CandidateProfileVersionRepository repository;
    private final ActiveProfileProvider profiles;
    private final ObjectMapper mapper;
    public PublishedProfileProvider(CandidateProfileVersionRepository repository, ActiveProfileProvider profiles, ObjectMapper mapper) {
        this.repository=repository; this.profiles=profiles; this.mapper=mapper;
    }
    public PublishedProfileSnapshot requireActive() {
        var profileId=profiles.requireProfileId();
        var v=repository.findByProfileIdAndActiveTrue(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("No active published profile version exists"));
        try { return new PublishedProfileSnapshot(v.id(), profileId, v.checksum(), mapper.readTree(v.snapshotJson())); }
        catch (Exception e) { throw new IllegalStateException("Published profile snapshot is invalid", e); }
    }
    public PublishedProfileSnapshot require(UUID versionId) {
        var profileId=profiles.requireProfileId();
        var v=repository.findById(versionId).filter(x->x.profileId().equals(profileId))
                .orElseThrow(() -> new ResourceNotFoundException("Published profile version was not found"));
        try { return new PublishedProfileSnapshot(v.id(), profileId, v.checksum(), mapper.readTree(v.snapshotJson())); }
        catch (Exception e) { throw new IllegalStateException("Published profile snapshot is invalid", e); }
    }
}
