package com.amit.jobagent.profile;
import java.util.UUID;
public interface ActiveProfileProvider {
    UUID requireProfileId();
    CandidateProfileResponse requireProfile();
    void lockForGeneration(UUID profileId);
}
