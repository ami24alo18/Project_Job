package com.amit.jobagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.amit.jobagent.profile.version.PublishedProfileSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationQuestionPolicyTest {
    private final ApplicationQuestionPolicy policy = new ApplicationQuestionPolicy();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deterministicallyAnswersCurrentLocationFromImmutableProfile() throws Exception {
        QuestionClassificationResult result = policy.classify(
                "What is your current location?", profile("Bengaluru, India", 74));

        assertThat(result.classification()).isEqualTo(QuestionClassification.VERIFIED_AUTOMATIC);
        assertThat(result.deterministicAnswer()).isEqualTo("Bengaluru, India");
        assertThat(result.answerStatus()).isEqualTo(QuestionAnswerStatus.DRAFTED);
        assertThat(result.confidence()).isEqualTo(100);
    }

    @Test
    void deterministicallyFormatsExperienceWithoutRoundingItUp() throws Exception {
        QuestionClassificationResult result = policy.classify(
                "How many years of total experience do you have?", profile("Bengaluru, India", 74));

        assertThat(result.classification()).isEqualTo(QuestionClassification.VERIFIED_AUTOMATIC);
        assertThat(result.deterministicAnswer()).isEqualTo("6 years and 2 months");
        assertThat(result.answerStatus()).isEqualTo(QuestionAnswerStatus.DRAFTED);
    }

    @Test
    void requiresUserInputForCurrentDecisionOrMissingPreference() throws Exception {
        QuestionClassificationResult compensation = policy.classify(
                "What is your desired salary?", profile("Bengaluru, India", 74));
        QuestionClassificationResult authorization = policy.classify(
                "Will you require work authorization sponsorship?", profile("Bengaluru, India", 74));

        assertThat(compensation.classification()).isEqualTo(QuestionClassification.USER_INPUT_REQUIRED);
        assertThat(compensation.deterministicAnswer()).isNull();
        assertThat(compensation.answerStatus()).isEqualTo(QuestionAnswerStatus.USER_INPUT_REQUIRED);
        assertThat(authorization.classification()).isEqualTo(QuestionClassification.USER_INPUT_REQUIRED);
        assertThat(authorization.deterministicAnswer()).isNull();
    }

    @Test
    void blocksSensitiveQuestionWithoutDraftingAnAnswer() throws Exception {
        QuestionClassificationResult result = policy.classify(
                "Please disclose any disability or medical condition.", profile("Bengaluru, India", 74));

        assertThat(result.classification()).isEqualTo(QuestionClassification.SENSITIVE_NEVER_AUTOMATIC);
        assertThat(result.deterministicAnswer()).isNull();
        assertThat(result.answerStatus()).isEqualTo(QuestionAnswerStatus.BLOCKED_SENSITIVE);
        assertThat(result.confidence()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "What is your date of birth?",
        "How old are you?",
        "Were you born in another country?",
        "Are you at least 18 years old?",
        "What is your national origin?",
        "Please provide your genetic information.",
        "Are you pregnant?",
        "Do you certify that the information in this application is accurate?",
        "Please confirm that every answer is true and complete.",
        "Type your full legal name as an electronic signature.",
        "Do you consent to a background screening?",
        "Provide authorization for a background check.",
        "I declare that all statements are complete.",
        "Do you agree to the terms and conditions?"
    })
    void failsClosedForDemographicAndLegallyBindingVariants(String question) throws Exception {
        QuestionClassificationResult result = policy.classify(question, profile("Bengaluru, India", 74));

        assertThat(result.classification()).isEqualTo(QuestionClassification.SENSITIVE_NEVER_AUTOMATIC);
        assertThat(result.deterministicAnswer()).isNull();
        assertThat(result.answerStatus()).isEqualTo(QuestionAnswerStatus.BLOCKED_SENSITIVE);
    }

    @Test
    void keepsProfessionalCertificationAndWorkAuthorizationOutOfLegalAttestationBucket() throws Exception {
        var professionalCertification = policy.classify(
                "Do you have an AWS certification?", profile("Bengaluru, India", 74));
        var workAuthorization = policy.classify(
                "Are you legally authorized to work in this country?", profile("Bengaluru, India", 74));

        assertThat(professionalCertification.classification())
                .isEqualTo(QuestionClassification.SUGGESTED_REQUIRES_REVIEW);
        assertThat(workAuthorization.classification()).isEqualTo(QuestionClassification.USER_INPUT_REQUIRED);
        assertThat(workAuthorization.deterministicAnswer()).isNull();
    }

    @Test
    void leavesSubjectiveLowRiskQuestionForReview() throws Exception {
        QuestionClassificationResult result = policy.classify(
                "Why are you interested in this role?", profile("Bengaluru, India", 74));

        assertThat(result.classification()).isEqualTo(QuestionClassification.SUGGESTED_REQUIRES_REVIEW);
        assertThat(result.deterministicAnswer()).isNull();
        assertThat(result.answerStatus()).isEqualTo(QuestionAnswerStatus.USER_INPUT_REQUIRED);
        assertThat(result.confidence()).isEqualTo(60);
    }

    @Test
    void doesNotInventLocationWhenPublishedProfileHasNoLocation() throws Exception {
        QuestionClassificationResult result = policy.classify(
                "What is your current location?", profile(null, 74));

        assertThat(result.classification()).isEqualTo(QuestionClassification.SUGGESTED_REQUIRES_REVIEW);
        assertThat(result.deterministicAnswer()).isNull();
    }

    @Test
    void suggestedDraftSelectsAJobRelevantFactInsteadOfTheFirstFact() {
        var unrelated = fact(
                "61000000-0000-0000-0000-000000000001",
                "EMPLOYMENT",
                "Managed retail inventory for a fictional store.",
                Set.of("Spreadsheets"),
                Set.of("Retail"));
        var relevant = fact(
                "61000000-0000-0000-0000-000000000002",
                "EMPLOYMENT",
                "Built reliable Java services for a backend platform.",
                Set.of("Java"),
                Set.of("Backend"));

        var result = policy.draftSuggested(
                "Why are you interested in this backend role?",
                List.of(unrelated, relevant),
                List.of("Backend Engineer", "Requires Java service development"));

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().fact()).isSameAs(relevant);
        assertThat(result.orElseThrow().answer()).contains(relevant.statement()).doesNotContain(unrelated.statement());
    }

    @Test
    void suggestedDraftRemainsEmptyWhenNoFactIsSemanticallyRelevant() {
        var unrelated = fact(
                "61000000-0000-0000-0000-000000000003",
                "EMPLOYMENT",
                "Managed retail inventory for a fictional store.",
                Set.of("Spreadsheets"),
                Set.of("Retail"));

        var result = policy.draftSuggested(
                "Summarize your relevant backend experience.",
                List.of(unrelated),
                List.of("Backend Engineer", "Requires Java and Kubernetes"));

        assertThat(result).isEmpty();
    }

    @Test
    void suggestedProjectDraftRequiresARelevantProjectFact() {
        var employment = fact(
                "61000000-0000-0000-0000-000000000004",
                "EMPLOYMENT",
                "Operated Kubernetes clusters.",
                Set.of("Kubernetes"),
                Set.of("Platform"));
        var project = fact(
                "61000000-0000-0000-0000-000000000005",
                "PROJECT",
                "Created a Kubernetes deployment project.",
                Set.of("Kubernetes"),
                Set.of("Platform"));

        var result = policy.draftSuggested(
                "Describe a Kubernetes project.", List.of(employment, project), List.of());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().fact()).isSameAs(project);
    }

    @Test
    void suggestedDraftDefensivelyRejectsSensitiveAndLegalQuestions() {
        var matching = fact(
                "61000000-0000-0000-0000-000000000006",
                "EMPLOYMENT",
                "Built Java services.",
                Set.of("Java"),
                Set.of("Backend"));

        assertThat(policy.draftSuggested(
                        "Do you certify that your Java experience is accurate?",
                        List.of(matching),
                        List.of("Java Engineer")))
                .isEmpty();
        assertThat(policy.draftSuggested(
                        "What is your date of birth for this Java role?",
                        List.of(matching),
                        List.of("Java Engineer")))
                .isEmpty();
    }

    private PublishedProfileSnapshot profile(String location, int experienceMonths) throws Exception {
        var root = mapper.createObjectNode();
        var profile = root.putObject("profile");
        if (location == null) {
            profile.putNull("currentLocation");
        } else {
            profile.put("currentLocation", location);
        }
        profile.put("totalExperienceMonths", experienceMonths);
        return new PublishedProfileSnapshot(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                "a".repeat(64),
                root);
    }

    private static VerifiedFactSnapshot fact(
            String id, String category, String statement, Set<String> skills, Set<String> domains) {
        return new VerifiedFactSnapshot(
                UUID.fromString(id), category, statement, null, null, null, skills, domains);
    }
}
