package com.amit.jobagent.application;

import com.amit.jobagent.job.JobMatchingProvider;
import com.amit.jobagent.matching.CompletedEvaluationProvider;
import com.amit.jobagent.matching.EvaluationRequirementProvider;
import com.amit.jobagent.matching.Recommendation;
import com.amit.jobagent.profile.version.PublishedProfileProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ApplicationPackageMapper {
    private final ApplicationPackageRepository packages;
    private final ApplicationPackageRevisionRepository revisions;
    private final GeneratedContentRepository contents;
    private final GeneratedClaimRepository claims;
    private final GeneratedClaimSourceRepository sources;
    private final ApplicationQuestionDraftRepository questions;
    private final DocumentArtifactRepository artifacts;
    private final JobMatchingProvider jobs;
    private final CompletedEvaluationProvider evaluations;
    private final EvaluationRequirementProvider requirements;
    private final PublishedProfileProvider profiles;
    private final ProfileGenerationSource profileSource;
    private final ResumeMatchScorer matchScorer;
    private final ObjectMapper mapper;

    ApplicationPackageMapper(
            ApplicationPackageRepository packages,
            ApplicationPackageRevisionRepository revisions,
            GeneratedContentRepository contents,
            GeneratedClaimRepository claims,
            GeneratedClaimSourceRepository sources,
            ApplicationQuestionDraftRepository questions,
            DocumentArtifactRepository artifacts,
            JobMatchingProvider jobs,
            CompletedEvaluationProvider evaluations,
            EvaluationRequirementProvider requirements,
            PublishedProfileProvider profiles,
            ProfileGenerationSource profileSource,
            ResumeMatchScorer matchScorer,
            ObjectMapper mapper) {
        this.packages = packages;
        this.revisions = revisions;
        this.contents = contents;
        this.claims = claims;
        this.sources = sources;
        this.questions = questions;
        this.artifacts = artifacts;
        this.jobs = jobs;
        this.evaluations = evaluations;
        this.requirements = requirements;
        this.profiles = profiles;
        this.profileSource = profileSource;
        this.matchScorer = matchScorer;
        this.mapper = mapper;
    }

    PackageSummaryResponse summary(ApplicationPackage applicationPackage) {
        var job = jobs.require(applicationPackage.jobId);
        ApplicationPackageRevision revision = applicationPackage.currentRevisionId == null
                ? null : revisions.findById(applicationPackage.currentRevisionId).orElse(null);
        var types = revision == null ? List.<ArtifactType>of() : artifacts.findByRevisionId(revision.id).stream()
                .map(artifact -> artifact.type).toList();
        return new PackageSummaryResponse(
                applicationPackage.getId(), applicationPackage.jobId, job.title(), job.company(),
                recommendation(revision, applicationPackage.jobId), applicationPackage.status,
                revision == null ? null : revision.revisionNumber,
                applicationPackage.getCreatedAt(), applicationPackage.getUpdatedAt(),
                applicationPackage.status == ApplicationPackageStatus.STALE, types);
    }

    PackageDetailResponse detail(ApplicationPackage applicationPackage) {
        var entity = applicationPackage.currentRevisionId == null
                ? null : revisions.findById(applicationPackage.currentRevisionId).orElse(null);
        return detail(applicationPackage, entity);
    }

    PackageDetailResponse detail(ApplicationPackage applicationPackage, ApplicationPackageRevision entity) {
        var job = jobs.require(applicationPackage.jobId);
        return new PackageDetailResponse(
                applicationPackage.getId(), applicationPackage.jobId, job.title(), job.company(),
                recommendation(entity, applicationPackage.jobId), applicationPackage.status,
                applicationPackage.createdBy, applicationPackage.staleReason, applicationPackage.getRecordVersion(),
                applicationPackage.getCreatedAt(), applicationPackage.getUpdatedAt(),
                entity == null ? null : revision(entity));
    }

    RevisionResponse revision(ApplicationPackageRevision revision) {
        var generatedContents = contents.findByRevisionIdOrderByOrderAscIdAsc(revision.id);
        var contentIds = generatedContents.stream().map(GeneratedContent::getId).toList();
        var generatedClaims = contentIds.isEmpty() ? List.<GeneratedClaim>of() : claims.findByContentIdIn(contentIds);
        var claimIds = generatedClaims.stream().map(claim -> claim.id).toList();
        var sourceList = claimIds.isEmpty() ? List.<GeneratedClaimSource>of() : sources.findByClaimIdIn(claimIds);
        var sourcesByClaim = new HashMap<UUID, List<GeneratedClaimSource>>();
        sourceList.forEach(source -> sourcesByClaim.computeIfAbsent(source.claimId, ignored -> new ArrayList<>()).add(source));
        return new RevisionResponse(
                revision.id, revision.packageId, revision.revisionNumber, revision.profileVersionId,
                revision.evaluationId, revision.status, revision.reviewStatus, revision.reviewRecordVersion,
                revision.jobChecksum, revision.profileChecksum, revision.evaluationChecksum,
                revision.promptVersion, revision.schemaVersion, revision.templateVersion, revision.model,
                revision.failureCode, revision.failureMessage, revision.createdAt, revision.completedAt,
                codes(revision.warnings), codes(revision.unsupportedRequirements),
                matchComparison(revision, generatedContents),
                generatedContents.stream().map(this::content).toList(),
                generatedClaims.stream().map(claim -> {
                    var claimSources = sourcesByClaim.getOrDefault(claim.id, List.of());
                    return new ClaimResponse(
                            claim.id, claim.contentId, claim.text, claim.type, claim.path, claim.validation,
                            codes(claim.codes),
                            claimSources.stream().map(GeneratedClaimSource::candidateEvidenceId)
                                    .filter(Objects::nonNull).toList(),
                            claimSources.stream().map(source -> source.requirementId)
                                    .filter(Objects::nonNull).toList(),
                            claimSources.stream().map(source -> source.jobField)
                                    .filter(Objects::nonNull).findFirst().orElse(null));
                }).toList(),
                questions.findByRevisionIdOrderByCreatedAtAscIdAsc(revision.id).stream()
                        .map(question -> new QuestionDraftResponse(
                                question.id, question.question, question.classification, question.answer,
                                question.answerStatus, question.confidence))
                        .toList(),
                artifacts.findByRevisionId(revision.id).stream()
                        .map(artifact -> new ArtifactResponse(
                                artifact.id, artifact.type, artifact.fileName, artifact.contentType,
                                artifact.size, artifact.checksum, artifact.templateVersion, artifact.createdAt))
                        .toList());
    }

    private ResumeMatchComparison matchComparison(
            ApplicationPackageRevision revision, List<GeneratedContent> generatedContents) {
        try {
            var applicationPackage = packages.findById(revision.packageId).orElse(null);
            if (applicationPackage == null) return null;
            var job = jobs.require(applicationPackage.jobId);
            var profile = profiles.require(revision.profileVersionId);
            return matchScorer.compare(
                    job, requirements.forEvaluation(revision.evaluationId),
                    profileSource.facts(profile), generatedContents);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    GeneratedContentResponse content(GeneratedContent content) {
        return new GeneratedContentResponse(
                content.getId(), content.type, content.key, content.order, content.text, content.origin,
                content.verification, content.userEdited, content.getRecordVersion(),
                content.getCreatedAt(), content.getUpdatedAt());
    }

    private Recommendation recommendation(ApplicationPackageRevision revision, UUID jobId) {
        if (revision == null) return null;
        try {
            return evaluations.require(revision.evaluationId, jobId).recommendation();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private List<String> codes(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of("STORED_VALIDATION_CODES_INVALID");
        }
    }
}
