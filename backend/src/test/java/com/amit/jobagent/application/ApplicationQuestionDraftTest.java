package com.amit.jobagent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationQuestionDraftTest {
    @Test
    void failClosedReclassificationClearsAnExistingDraftAnswer() {
        var question = new ApplicationQuestionDraft(
                UUID.randomUUID(),
                "Do you certify this declaration?",
                "a".repeat(64),
                "do_you_certify_this_declaration",
                QuestionClassification.SUGGESTED_REQUIRES_REVIEW,
                null,
                QuestionAnswerStatus.USER_INPUT_REQUIRED,
                60);
        question.answer("An obsolete unsafe draft", 80);

        question.applyClassification(new QuestionClassificationResult(
                QuestionClassification.SENSITIVE_NEVER_AUTOMATIC,
                null,
                QuestionAnswerStatus.BLOCKED_SENSITIVE,
                null));

        assertThat(question.classification).isEqualTo(QuestionClassification.SENSITIVE_NEVER_AUTOMATIC);
        assertThat(question.answer).isNull();
        assertThat(question.answerStatus).isEqualTo(QuestionAnswerStatus.BLOCKED_SENSITIVE);
        assertThat(question.confidence).isNull();
    }
}
