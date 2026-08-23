package com.amit.jobagent.application;

import java.util.List;

public interface ApplicationContentGenerator {
    TailoringPlan plan(ApplicationContentGenerationRequest request);

    GeneratedApplicationContent generate(
            ApplicationContentGenerationRequest request, TailoringPlan plan);

    /** One bounded provenance/schema repair; providers may include only safe validation codes. */
    default GeneratedApplicationContent repair(
            ApplicationContentGenerationRequest request,
            TailoringPlan plan,
            List<String> validationCodes) {
        return generate(request, plan);
    }
}
