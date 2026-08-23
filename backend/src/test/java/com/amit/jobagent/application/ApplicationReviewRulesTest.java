package com.amit.jobagent.application;
import static org.assertj.core.api.Assertions.assertThat;import java.util.UUID;import org.junit.jupiter.api.Test;
class ApplicationReviewRulesTest{
 @Test void checklistRequiresEveryHumanConfirmation(){assertThat(new ReviewChecklist(true,true,true,true,true,true).complete()).isTrue();assertThat(new ReviewChecklist(true,true,true,false,true,true).complete()).isFalse();}
 @Test void readyRevisionEntersPendingReviewAndMaterialChangeInvalidatesApproval(){var r=new ApplicationPackageRevision(UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),"j","p","e","prompt","schema","template","model","settings",null,"cache");assertThat(r.reviewStatus).isEqualTo(ReviewStatus.NOT_READY);r.ready();assertThat(r.reviewStatus).isEqualTo(ReviewStatus.PENDING_REVIEW);r.review(ReviewStatus.APPROVED_FOR_HANDOFF);r.status(RevisionStatus.STALE);assertThat(r.reviewStatus).isEqualTo(ReviewStatus.INVALIDATED);}
 @Test void sensitiveResolutionIsAlwaysExplicit(){assertThat(QuestionResolution.values()).containsExactly(QuestionResolution.ACKNOWLEDGED,QuestionResolution.COMPLETED,QuestionResolution.INTENTIONALLY_EXCLUDED);}
}
