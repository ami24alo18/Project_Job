package com.amit.jobagent.application;

import java.util.Optional;

interface SemanticResumeMatchProvider {
    Optional<Scores> score(String jobDescription, String currentResume, String generatedResume);

    record Scores(int currentResumeScore, int generatedResumeScore, String method) {}
}
