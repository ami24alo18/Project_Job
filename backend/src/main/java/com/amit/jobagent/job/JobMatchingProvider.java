package com.amit.jobagent.job;

import com.amit.jobagent.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JobMatchingProvider {
    private final JobPostingRepository repository;
    public JobMatchingProvider(JobPostingRepository repository) { this.repository = repository; }
    public JobMatchingView require(UUID id) {
        var j = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Job was not found"));
        return new JobMatchingView(j.getId(), j.company(), j.title(), j.location(), j.workplaceType(),
                j.employmentType(), j.descriptionPlainText(), j.expiresAt(), j.contentHash(), j.status());
    }
}
