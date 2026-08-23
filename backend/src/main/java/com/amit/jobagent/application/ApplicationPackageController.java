package com.amit.jobagent.application;

import com.amit.jobagent.common.api.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import com.amit.jobagent.matching.Recommendation;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ApplicationPackageController {
    private final ApplicationPackageService service;
    private final ApplicationReviewService reviews;
    private final HandoffEligibilityService eligibility;

    public ApplicationPackageController(ApplicationPackageService service, ApplicationReviewService reviews, HandoffEligibilityService eligibility) { this.service = service; this.reviews = reviews; this.eligibility = eligibility; }

    @GetMapping("/application-packages/review-queue")
    PagedResponse<ReviewQueueItem> reviewQueue(@RequestParam(required=false) ReviewStatus reviewStatus,
            @RequestParam(required=false) Recommendation recommendation,@RequestParam(required=false) String company,
            @RequestParam(required=false) Boolean stale,@RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return reviews.queue(reviewStatus,recommendation,company,stale,from,to,page,size);
    }

    @GetMapping("/application-packages/{packageId}/revisions/{revisionId}/review")
    ApplicationReviewResponse review(@PathVariable UUID packageId,@PathVariable UUID revisionId){return reviews.get(packageId,revisionId);}

    @GetMapping("/application-packages/{packageId}/revisions/{revisionId}/handoff-eligibility")
    HandoffEligibility eligibility(@PathVariable UUID packageId,@PathVariable UUID revisionId){return eligibility.check(packageId,revisionId);}

    @GetMapping("/application-packages/{packageId}/revisions/{revisionId}/resume/preview")
    ResponseEntity<byte[]> revisionPreview(@PathVariable UUID packageId,@PathVariable UUID revisionId){return response(service.downloadRevision(packageId,revisionId,ArtifactType.HTML_PREVIEW),false);}
    @GetMapping("/application-packages/{packageId}/revisions/{revisionId}/resume/pdf")
    ResponseEntity<byte[]> revisionPdf(@PathVariable UUID packageId,@PathVariable UUID revisionId){return response(service.downloadRevision(packageId,revisionId,ArtifactType.PDF_RESUME),true);}
    @GetMapping("/application-packages/{packageId}/revisions/{revisionId}/resume/docx")
    ResponseEntity<byte[]> revisionDocx(@PathVariable UUID packageId,@PathVariable UUID revisionId){return response(service.downloadRevision(packageId,revisionId,ArtifactType.DOCX_RESUME),true);}

    @PostMapping("/application-packages/{packageId}/revisions/{revisionId}/review/approve")
    ApplicationReviewResponse approve(@PathVariable UUID packageId,@PathVariable UUID revisionId,@Valid @RequestBody ReviewDecisionRequest request){return reviews.decide(packageId,revisionId,ReviewDecisionType.APPROVED_FOR_HANDOFF,request);}

    @PostMapping("/application-packages/{packageId}/revisions/{revisionId}/review/request-changes")
    ApplicationReviewResponse requestChanges(@PathVariable UUID packageId,@PathVariable UUID revisionId,@Valid @RequestBody ReviewDecisionRequest request){return reviews.decide(packageId,revisionId,ReviewDecisionType.CHANGES_REQUESTED,request);}

    @PostMapping("/application-packages/{packageId}/revisions/{revisionId}/review/reject")
    ApplicationReviewResponse reject(@PathVariable UUID packageId,@PathVariable UUID revisionId,@Valid @RequestBody ReviewDecisionRequest request){return reviews.decide(packageId,revisionId,ReviewDecisionType.REJECTED,request);}

    @PostMapping("/jobs/{jobId}/application-packages")
    ResponseEntity<PackageDetailResponse> create(
            @PathVariable UUID jobId,
            @Valid @RequestBody(required = false) ApplicationPackageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-N8N-Automation", defaultValue = "false") boolean automated) {
        var body = service.create(jobId, request, idempotencyKey, automated);
        return ResponseEntity.accepted().location(URI.create("/api/v1/application-packages/" + body.id())).body(body);
    }

    @GetMapping("/application-packages")
    PagedResponse<PackageSummaryResponse> list(
            @RequestParam(required = false) ApplicationPackageStatus status,
            @RequestParam(required = false) Boolean stale,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return service.list(status, stale, page, size, sort);
    }

    @GetMapping("/application-packages/{packageId}")
    PackageDetailResponse get(@PathVariable UUID packageId) { return service.get(packageId); }

    @GetMapping("/application-packages/{packageId}/revisions")
    List<RevisionResponse> revisions(@PathVariable UUID packageId) { return service.revisions(packageId); }

    @GetMapping("/application-packages/{packageId}/revisions/{revisionId}")
    RevisionResponse revision(@PathVariable UUID packageId, @PathVariable UUID revisionId) {
        return service.revision(packageId, revisionId);
    }

    @PostMapping("/application-packages/{packageId}/regenerate")
    ResponseEntity<PackageDetailResponse> regenerate(
            @PathVariable UUID packageId,
            @Valid @RequestBody(required = false) RegeneratePackageRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        var body = service.regenerate(packageId, request, idempotencyKey);
        return ResponseEntity.accepted().location(URI.create("/api/v1/application-packages/" + body.id())).body(body);
    }

    @PutMapping("/application-packages/{packageId}/content/{contentId}")
    GeneratedContentResponse edit(
            @PathVariable UUID packageId, @PathVariable UUID contentId,
            @Valid @RequestBody GeneratedContentUpdateRequest request) {
        return service.editContent(packageId, contentId, request);
    }

    @PostMapping("/application-packages/{packageId}/validate")
    PackageValidationResponse validate(@PathVariable UUID packageId) { return service.validate(packageId); }

    @PostMapping("/application-packages/{packageId}/questions")
    List<QuestionDraftResponse> questions(
            @PathVariable UUID packageId, @Valid @RequestBody QuestionsRequest request) {
        return service.addQuestions(packageId, request);
    }

    @PostMapping("/application-packages/{packageId}/questions/draft")
    List<QuestionDraftResponse> draftQuestions(@PathVariable UUID packageId) {
        return service.draftQuestions(packageId);
    }

    @GetMapping("/application-packages/{packageId}/resume/preview")
    ResponseEntity<byte[]> preview(@PathVariable UUID packageId) {
        return response(service.download(packageId, ArtifactType.HTML_PREVIEW), false);
    }

    @GetMapping("/application-packages/{packageId}/resume/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable UUID packageId) {
        return response(service.download(packageId, ArtifactType.PDF_RESUME), true);
    }

    @GetMapping("/application-packages/{packageId}/resume/docx")
    ResponseEntity<byte[]> docx(@PathVariable UUID packageId) {
        return response(service.download(packageId, ArtifactType.DOCX_RESUME), true);
    }

    @PostMapping("/application-packages/{packageId}/archive")
    PackageDetailResponse archive(@PathVariable UUID packageId) { return service.archive(packageId); }

    private static ResponseEntity<byte[]> response(ArtifactDownload artifact, boolean attachment) {
        var disposition = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(artifact.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .contentLength(artifact.bytes().length)
                .body(artifact.bytes());
    }
}
