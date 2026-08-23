package com.amit.jobagent.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "application_question_draft")
class ApplicationQuestionDraft {
    @Id UUID id;
    @Column(name = "package_revision_id", nullable = false, updatable = false) UUID revisionId;
    @Column(nullable = false, length = 2000) String question;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "question_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    String hash;
    @Column(name = "normalized_question_key", nullable = false, length = 500) String normalizedKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) QuestionClassification classification;
    @Column(name = "draft_answer", columnDefinition = "text") String answer;
    @Enumerated(EnumType.STRING) @Column(name = "answer_status", nullable = false, length = 30) QuestionAnswerStatus answerStatus;
    Integer confidence;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    protected ApplicationQuestionDraft() {}

    ApplicationQuestionDraft(
            UUID revision,
            String question,
            String hash,
            String key,
            QuestionClassification classification,
            String answer,
            QuestionAnswerStatus status,
            Integer confidence) {
        id = UUID.randomUUID();
        revisionId = revision;
        this.question = question;
        this.hash = hash;
        normalizedKey = key;
        this.classification = classification;
        this.answer = answer;
        answerStatus = status;
        this.confidence = confidence;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    void answer(String value, int confidence) {
        answer = value;
        this.confidence = confidence;
        answerStatus = QuestionAnswerStatus.DRAFTED;
        updatedAt = Instant.now();
    }

    void applyClassification(QuestionClassificationResult result) {
        classification = result.classification();
        answer = result.deterministicAnswer();
        answerStatus = result.answerStatus();
        confidence = result.confidence();
        updatedAt = Instant.now();
    }
}
