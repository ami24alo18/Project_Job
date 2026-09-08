package com.amit.jobagent.application;

import java.util.List;

public record ResumeMatchComparison(
        int currentResumeScore,
        int generatedDraftScore,
        int scoreDelta,
        String method,
        List<String> matchedKeywords,
        List<String> missingKeywords) {}
