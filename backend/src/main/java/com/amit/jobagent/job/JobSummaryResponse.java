package com.amit.jobagent.job;

public record JobSummaryResponse(long total,long readyForEvaluation,long needsReview,long duplicates,long expired,long sourceRemoved,long archived) {}
