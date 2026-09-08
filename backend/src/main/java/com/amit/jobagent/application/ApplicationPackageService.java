package com.amit.jobagent.application;

import com.amit.jobagent.application.render.DeterministicResumeRenderer;
import com.amit.jobagent.application.render.ResumeContact;
import com.amit.jobagent.application.render.ResumeDocumentModel;
import com.amit.jobagent.application.render.ResumeEntry;
import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.config.JobAgentProperties;
import com.amit.jobagent.common.api.PagedResponse;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import com.amit.jobagent.document.ObjectStorage;
import com.amit.jobagent.job.JobMatchingProvider;
import com.amit.jobagent.job.JobMatchingView;
import com.amit.jobagent.job.JobPostingStatus;
import com.amit.jobagent.matching.CompletedEvaluationProvider;
import com.amit.jobagent.matching.CompletedEvaluationSnapshot;
import com.amit.jobagent.matching.EvaluationRequirementProvider;
import com.amit.jobagent.matching.Recommendation;
import com.amit.jobagent.profile.ActiveProfileProvider;
import com.amit.jobagent.profile.version.PublishedProfileProvider;
import com.amit.jobagent.profile.version.PublishedProfileSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ApplicationPackageService {
    private static final Pattern DATE_RANGE_IN_TEXT = Pattern.compile(
            "(?iu)(\\b(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?"
                    + "(?:19|20)\\d{2}\\b\\s*[\\u2013\\u2014-]\\s*"
                    + "(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+)?"
                    + "(?:\\b(?:19|20)\\d{2}\\b|present))");
    private static final Set<JobPostingStatus> ELIGIBLE_JOB_STATUSES =
            Set.of(JobPostingStatus.READY_FOR_EVALUATION, JobPostingStatus.NEEDS_REVIEW);

    private final JobMatchingProvider jobs;
    private final CompletedEvaluationProvider evaluations;
    private final EvaluationRequirementProvider requirements;
    private final PublishedProfileProvider profiles;
    private final ActiveProfileProvider activeProfiles;
    private final ProfileGenerationSource profileSource;
    private final VerifiedFactInputPolicy factInputPolicy;
    private final ApplicationContentGenerator generator;
    private final GeneratedContentValidator validator;
    private final EvidenceBoundContentMerger contentMerger;
    private final ApplicationQuestionPolicy questionPolicy;
    private final ApplicationPackageRepository packages;
    private final ApplicationPackageRevisionRepository revisions;
    private final ApplicationPackageIdempotencyAliasRepository idempotencyAliases;
    private final GeneratedContentRepository contents;
    private final GeneratedClaimRepository claims;
    private final GeneratedClaimSourceRepository claimSources;
    private final ApplicationQuestionDraftRepository questions;
    private final DocumentArtifactRepository artifacts;
    private final ApplicationPackageMapper responseMapper;
    private final DeterministicResumeRenderer renderer;
    private final ObjectStorage storage;
    private final JobAgentProperties jobAgentProperties;
    private final ContentGenerationProperties generationProperties;
    private final ApplicationGenerationPromptCatalog promptCatalog;
    private final AuditService audit;
    private final ApplicationGenerationMetrics metrics;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public ApplicationPackageService(
            JobMatchingProvider jobs,
            CompletedEvaluationProvider evaluations,
            EvaluationRequirementProvider requirements,
            PublishedProfileProvider profiles,
            ActiveProfileProvider activeProfiles,
            ProfileGenerationSource profileSource,
            VerifiedFactInputPolicy factInputPolicy,
            ApplicationContentGenerator generator,
            GeneratedContentValidator validator,
            EvidenceBoundContentMerger contentMerger,
            ApplicationQuestionPolicy questionPolicy,
            ApplicationPackageRepository packages,
            ApplicationPackageRevisionRepository revisions,
            ApplicationPackageIdempotencyAliasRepository idempotencyAliases,
            GeneratedContentRepository contents,
            GeneratedClaimRepository claims,
            GeneratedClaimSourceRepository claimSources,
            ApplicationQuestionDraftRepository questions,
            DocumentArtifactRepository artifacts,
            ApplicationPackageMapper responseMapper,
            DeterministicResumeRenderer renderer,
            ObjectStorage storage,
            JobAgentProperties jobAgentProperties,
            ContentGenerationProperties generationProperties,
            ApplicationGenerationPromptCatalog promptCatalog,
            AuditService audit,
            ApplicationGenerationMetrics metrics,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.evaluations = evaluations;
        this.requirements = requirements;
        this.profiles = profiles;
        this.activeProfiles = activeProfiles;
        this.profileSource = profileSource;
        this.factInputPolicy = factInputPolicy;
        this.generator = generator;
        this.validator = validator;
        this.contentMerger = contentMerger;
        this.questionPolicy = questionPolicy;
        this.packages = packages;
        this.revisions = revisions;
        this.idempotencyAliases = idempotencyAliases;
        this.contents = contents;
        this.claims = claims;
        this.claimSources = claimSources;
        this.questions = questions;
        this.artifacts = artifacts;
        this.responseMapper = responseMapper;
        this.renderer = renderer;
        this.storage = storage;
        this.jobAgentProperties = jobAgentProperties;
        this.generationProperties = generationProperties;
        this.promptCatalog = promptCatalog;
        this.audit = audit;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public PackageDetailResponse create(
            UUID jobId, ApplicationPackageRequest request, String idempotencyKey, boolean automated) {
        var replay = replayAcceptedIdempotencyKey(jobId, idempotencyKey, false);
        if (replay != null) return replay;
        var safeRequest = request == null ? new ApplicationPackageRequest(null, null) : request;
        return generate(jobId, safeRequest.evaluationId(), safeRequest.overrideReason(), idempotencyKey, automated, false, false);
    }

    public PackageDetailResponse regenerate(UUID packageId, RegeneratePackageRequest request, String idempotencyKey) {
        var safeRequest = request == null ? new RegeneratePackageRequest(false, null) : request;
        var existing = requireOwned(packageId);
        var replay = replayAcceptedIdempotencyKey(existing.jobId, idempotencyKey, true);
        if (replay != null) return replay;
        if (existing.currentRevisionId != null
                && contents.existsByRevisionIdAndUserEditedTrue(existing.currentRevisionId)
                && !safeRequest.replaceUserEdited()) {
            throw new ConflictException("Regeneration would replace user-edited content; explicitly allow replacement");
        }
        return generate(existing.jobId, null, safeRequest.reason(), idempotencyKey, false, true, safeRequest.replaceUserEdited());
    }

    private PackageDetailResponse replayAcceptedIdempotencyKey(
            UUID jobId, String idempotencyKey, boolean regeneration) {
        if (isBlank(idempotencyKey)) return null;
        UUID candidateId = activeProfiles.requireProfileId();
        String idempotencyHash = idempotencyHash(candidateId, jobId, regeneration, idempotencyKey);
        var context = transactions.execute(status -> {
            var revision = idempotentRevision(idempotencyHash);
            if (revision == null) return null;
            var result = idempotentReplayContext(revision, candidateId);
            bindIdempotencyAlias(idempotencyHash, revision.id);
            return result;
        });
        if (context == null) return null;
        metrics.cacheHit();
        return responseMapper.detail(requireOwned(context.packageId()), requireRevision(context.revisionId()));
    }

    public PagedResponse<PackageSummaryResponse> list(
            ApplicationPackageStatus status, Boolean stale, int requestedPage, int requestedSize, String sort) {
        UUID candidateId = activeProfiles.requireProfileId();
        int page = Math.max(requestedPage, 0);
        int size = Math.min(Math.max(requestedSize, 1), 100);
        var filtered = packages.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
                .map(this::refreshStaleness)
                .filter(p -> status == null || p.status == status)
                .filter(p -> stale == null || (p.status == ApplicationPackageStatus.STALE) == stale)
                .sorted(packageSort(sort))
                .map(responseMapper::summary)
                .toList();
        int from = (int) Math.min((long) page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        int totalPages = filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size;
        return new PagedResponse<>(filtered.subList(from, to), page, size, filtered.size(), totalPages);
    }

    private static Comparator<ApplicationPackage> packageSort(String requestedSort) {
        String[] parts = Objects.toString(requestedSort, "updatedAt,desc").split(",", 2);
        Comparator<ApplicationPackage> comparator = "createdAt".equals(parts[0])
                ? Comparator.comparing(ApplicationPackage::getCreatedAt)
                : Comparator.comparing(ApplicationPackage::getUpdatedAt);
        if (parts.length < 2 || !"asc".equalsIgnoreCase(parts[1])) comparator = comparator.reversed();
        return comparator.thenComparing(ApplicationPackage::getId);
    }

    public PackageDetailResponse get(UUID packageId) {
        return responseMapper.detail(refreshStaleness(requireOwned(packageId)));
    }

    public List<RevisionResponse> revisions(UUID packageId) {
        var applicationPackage = requireOwned(packageId);
        return revisions.findByPackageIdOrderByRevisionNumberDesc(applicationPackage.getId()).stream()
                .map(responseMapper::revision).toList();
    }

    public RevisionResponse revision(UUID packageId, UUID revisionId) {
        requireOwned(packageId);
        var revision = revisions.findByPackageIdAndId(packageId, revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Package revision was not found"));
        return responseMapper.revision(revision);
    }

    public GeneratedContentResponse editContent(UUID packageId, UUID contentId, GeneratedContentUpdateRequest request) {
        Objects.requireNonNull(request, "request is required");
        return transactions.execute(status -> {
            var applicationPackage = requireOwnedForUpdate(packageId);
            if (applicationPackage.status != ApplicationPackageStatus.READY
                    && applicationPackage.status != ApplicationPackageStatus.STALE) {
                throw new ConflictException("Content can be edited only when the current draft is not generating or archived");
            }
            if (applicationPackage.currentRevisionId == null) throw new ConflictException("The package has no current revision");
            var content = contents.findByIdAndRevisionId(contentId, applicationPackage.currentRevisionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Generated content was not found in the current revision"));
            if (content.getRecordVersion() != request.recordVersion()) throw new ConflictException("Generated content was changed by another request");
            content.edit(request.text().trim());
            applicationPackage.stale("USER_EDIT_REQUIRES_REVALIDATION_AND_REGENERATION");
            var revision = requireRevision(applicationPackage.currentRevisionId);
            revision.status(RevisionStatus.STALE);
            var editedClaims = claims.findByContentIdIn(List.of(contentId));
            editedClaims.forEach(GeneratedClaim::requireReviewAfterUserEdit);
            contents.saveAndFlush(content);
            claims.saveAll(editedClaims);
            revisions.save(revision);
            packages.saveAndFlush(applicationPackage);
            audit.record(AuditEventType.APPLICATION_CONTENT_EDITED, "ApplicationPackage", packageId,
                    json(Map.of("contentId", contentId.toString())));
            return responseMapper.content(content);
        });
    }

    public PackageValidationResponse validate(UUID packageId) {
        var applicationPackage = requireOwned(packageId);
        if (applicationPackage.currentRevisionId == null) return new PackageValidationResponse(false, List.of("NO_CURRENT_REVISION"));
        var revision = requireRevision(applicationPackage.currentRevisionId);
        var storedContents = contents.findByRevisionIdOrderByOrderAscIdAsc(revision.id);
        var contentIds = storedContents.stream().map(GeneratedContent::getId).toList();
        var storedClaims = contentIds.isEmpty() ? List.<GeneratedClaim>of() : claims.findByContentIdIn(contentIds);
        var sourcesByClaim = sourcesByClaim(storedClaims);
        var contentById = new LinkedHashMap<UUID, GeneratedContent>();
        storedContents.forEach(c -> contentById.put(c.getId(), c));
        var generated = new GeneratedApplicationContent(
                storedContents.stream().map(c -> new GeneratedContentItem(c.type, c.key, c.order, c.text)).toList(),
                storedClaims.stream().map(c -> {
                    var src = sourcesByClaim.getOrDefault(c.id, List.of());
                    return new GeneratedClaimAtom(contentById.get(c.contentId).key, c.text, c.type,
                            src.stream().map(GeneratedClaimSource::candidateEvidenceId).filter(Objects::nonNull).toList(),
                            src.stream().map(s -> s.requirementId).filter(Objects::nonNull).toList(),
                            src.stream().map(s -> s.jobField).filter(Objects::nonNull).findFirst().orElse(null));
                }).toList(), List.of(), List.of());
        var profile = profiles.require(revision.profileVersionId);
        var requirementViews = requirements.forEvaluation(revision.evaluationId);
        var job = jobs.require(applicationPackage.jobId);
        var result = validator.validate(
                generated,
                profileSource.facts(profile),
                exactJobEvidence(job, requirementViews));
        var codes = new LinkedHashSet<>(result.codes());
        if (storedContents.stream().anyMatch(c -> c.userEdited && c.verification != ContentVerificationStatus.VERIFIED)) {
            codes.add("USER_EDITED_CONTENT_REQUIRES_REVALIDATION");
        }
        var response = new PackageValidationResponse(codes.isEmpty(), List.copyOf(codes));
        if (!response.valid() && revision.reviewStatus == ReviewStatus.APPROVED_FOR_HANDOFF) {
            transactions.executeWithoutResult(tx -> {
                var lockedPackage = requireOwnedForUpdate(packageId);
                var lockedRevision = requireRevision(revision.id);
                lockedRevision.review(ReviewStatus.INVALIDATED);
                lockedPackage.stale("VALIDATION_FAILED_AFTER_APPROVAL");
                revisions.save(lockedRevision);
                packages.saveAndFlush(lockedPackage);
                audit.record(AuditEventType.APPLICATION_REVIEW_INVALIDATED, "ApplicationPackageRevision", revision.id,
                        json(Map.of("reason", "VALIDATION_FAILED_AFTER_APPROVAL")));
            });
        }
        audit.record(AuditEventType.APPLICATION_PACKAGE_VALIDATED, "ApplicationPackage", packageId,
                json(Map.of("valid", response.valid(), "codeCount", response.validationCodes().size())));
        return response;
    }

    public List<QuestionDraftResponse> addQuestions(UUID packageId, QuestionsRequest request) {
        Objects.requireNonNull(request, "request is required");
        return transactions.execute(status -> {
            var applicationPackage = requireOwned(packageId);
            var revision = currentRevision(applicationPackage);
            var profile = profiles.require(revision.profileVersionId);
            for (String raw : request.questions()) {
                String question = raw.trim();
                String hash = hash(normalize(question));
                if (questions.existsByRevisionIdAndHash(revision.id, hash)) continue;
                var classification = questionPolicy.classify(question, profile);
                questions.save(new ApplicationQuestionDraft(revision.id, question, hash,
                        limit(normalize(question).replace(' ', '_'), 500), classification.classification(),
                        classification.deterministicAnswer(), classification.answerStatus(), classification.confidence()));
            }
            return questions.findByRevisionIdOrderByCreatedAtAscIdAsc(revision.id).stream()
                    .map(q -> new QuestionDraftResponse(q.id, q.question, q.classification, q.answer, q.answerStatus, q.confidence)).toList();
        });
    }

    public List<QuestionDraftResponse> draftQuestions(UUID packageId) {
        return transactions.execute(status -> {
            var applicationPackage = requireOwned(packageId);
            var revision = currentRevision(applicationPackage);
            var profile = profiles.require(revision.profileVersionId);
            var facts = factInputPolicy.safeForGeneration(profile, profileSource.facts(profile));
            var job = jobs.require(applicationPackage.jobId);
            var jobContext = new ArrayList<String>();
            jobContext.add(job.title());
            jobContext.add(job.description());
            requirements.forEvaluation(revision.evaluationId).stream()
                    .map(EvaluationRequirementProvider.RequirementView::text)
                    .forEach(jobContext::add);
            for (var question : questions.findByRevisionIdOrderByCreatedAtAscIdAsc(revision.id)) {
                var currentPolicy = questionPolicy.classify(question.question, profile);
                if (currentPolicy.classification() != QuestionClassification.SUGGESTED_REQUIRES_REVIEW) {
                    question.applyClassification(currentPolicy);
                    questions.save(question);
                    continue;
                }
                if (question.classification != QuestionClassification.SUGGESTED_REQUIRES_REVIEW) {
                    question.applyClassification(currentPolicy);
                    questions.save(question);
                }
                if (question.answer != null || facts.isEmpty()) continue;
                var suggested = questionPolicy.draftSuggested(question.question, facts, jobContext);
                if (suggested.isEmpty()) continue;
                var draft = suggested.get();
                var fact = draft.fact();
                question.answer(draft.answer(), 80);
                questions.save(question);
                String key = "question-" + question.id;
                var content = contents.save(new GeneratedContent(revision.id, GeneratedContentType.APPLICATION_ANSWER,
                        key, nextContentOrder(revision.id), draft.answer(), ContentOrigin.DETERMINISTIC, ContentVerificationStatus.VERIFIED));
                claims.save(new GeneratedClaim(content.getId(), draft.nonFactualText(),
                        ClaimType.NON_FACTUAL, key, ClaimValidationStatus.VALID, "[]"));
                var factual = claims.save(new GeneratedClaim(content.getId(), fact.statement(),
                        ClaimType.CANDIDATE_FACT, key, ClaimValidationStatus.VALID, "[]"));
                claimSources.save(GeneratedClaimSource.fromSnapshotEvidence(factual.id, fact.id()));
            }
            return questions.findByRevisionIdOrderByCreatedAtAscIdAsc(revision.id).stream()
                    .map(q -> new QuestionDraftResponse(q.id, q.question, q.classification, q.answer, q.answerStatus, q.confidence)).toList();
        });
    }

    public ArtifactDownload download(UUID packageId, ArtifactType type) {
        var applicationPackage = refreshStaleness(requireOwned(packageId));
        if (applicationPackage.status != ApplicationPackageStatus.READY) {
            throw new ConflictException("Resume artifacts are unavailable until the current draft is ready and up to date");
        }
        var revision = currentRevision(applicationPackage);
        if (revision.status != RevisionStatus.READY) {
            throw new ConflictException("Resume artifacts are unavailable until the current revision is ready and up to date");
        }
        var artifact = artifacts.findByRevisionIdAndType(revision.id, type)
                .orElseThrow(() -> new ResourceNotFoundException("Requested resume artifact was not found"));
        byte[] bytes = storage.load(artifact.storageKey);
        if (bytes.length != artifact.size || !hash(bytes).equals(artifact.checksum)) {
            throw new IllegalStateException("Stored resume artifact failed integrity validation");
        }
        audit.record(AuditEventType.DOCUMENT_ARTIFACT_DOWNLOADED, "ApplicationPackage", packageId,
                json(Map.of("artifactType", type.name())));
        return new ArtifactDownload(artifact.fileName, artifact.contentType, bytes);
    }

    public ArtifactDownload downloadRevision(UUID packageId, UUID revisionId, ArtifactType type) {
        requireOwned(packageId);
        var revision = revisions.findByPackageIdAndId(packageId, revisionId)
                .orElseThrow(() -> new ResourceNotFoundException("Package revision was not found"));
        var artifact = artifacts.findByRevisionIdAndType(revision.id, type)
                .orElseThrow(() -> new ResourceNotFoundException("Requested revision artifact was not found"));
        byte[] bytes = storage.load(artifact.storageKey);
        if (bytes.length != artifact.size || !hash(bytes).equals(artifact.checksum))
            throw new IllegalStateException("Stored resume artifact failed integrity validation");
        audit.record(AuditEventType.DOCUMENT_ARTIFACT_DOWNLOADED, "ApplicationPackageRevision", revision.id,
                json(Map.of("artifactType", type.name())));
        return new ArtifactDownload(artifact.fileName, artifact.contentType, bytes);
    }

    public PackageDetailResponse archive(UUID packageId) {
        return transactions.execute(status -> {
            var applicationPackage = requireOwnedForUpdate(packageId);
            applicationPackage.archive();
            packages.saveAndFlush(applicationPackage);
            audit.record(AuditEventType.APPLICATION_PACKAGE_ARCHIVED, "ApplicationPackage", packageId, "{}");
            return responseMapper.detail(applicationPackage);
        });
    }

    private PackageDetailResponse generate(
            UUID jobId, UUID requestedEvaluationId, String reason, String idempotencyKey,
            boolean automated, boolean regeneration, boolean replaceUserEdited) {
        var job = jobs.require(jobId);
        if (!generationProperties.templateVersion().equals(DeterministicResumeRenderer.TEMPLATE_VERSION)) {
            throw new DomainValidationException("The configured resume template version is not installed");
        }
        if (isBlank(job.description())) {
            throw new DomainValidationException(
                    "Add the complete job description, save the job, and evaluate it again before generating a draft");
        }
        if (!ELIGIBLE_JOB_STATUSES.contains(job.status()) || (job.expiresAt() != null && job.expiresAt().isBefore(Instant.now()))) {
            throw new DomainValidationException("Only active jobs can produce application packages");
        }
        CompletedEvaluationSnapshot evaluation = requestedEvaluationId == null
                ? evaluations.requireLatest(jobId) : evaluations.require(requestedEvaluationId, jobId);
        if (!Objects.equals(job.contentHash(), evaluation.jobChecksum()) || evaluation.stale()) {
            throw new DomainValidationException("The selected evaluation is stale; evaluate the current job before generation");
        }
        var profile = profiles.require(evaluation.profileVersionId());
        if (!Objects.equals(profile.checksum(), evaluation.profileChecksum())) {
            throw new DomainValidationException("The selected evaluation does not match its immutable profile snapshot");
        }
        var verifiedFacts = profileSource.facts(profile);
        if (verifiedFacts.isEmpty()) throw new DomainValidationException("At least one verified candidate fact is required");
        var generationFacts = factInputPolicy.safeForGeneration(profile, verifiedFacts);
        if (generationFacts.isEmpty()) {
            throw new DomainValidationException("At least one privacy-safe verified candidate fact is required");
        }
        if (automated) validateAutomationEligibility(evaluation);

        var requirementViews = requirements.forEvaluation(evaluation.id());
        var requirementIds = requirementViews.stream().map(EvaluationRequirementProvider.RequirementView::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var jobEvidence = exactJobEvidence(job, requirementViews);
        var generationRequirements = requirementViews.stream().map(r -> new JobRequirementSnapshot(
                r.id(), r.text(), r.type(), r.category(), r.matchStatus(), r.jobEvidence())).toList();
        var request = new ApplicationContentGenerationRequest(profile.id(), profile.checksum(), job.contentHash(),
                evaluation.checksum(), job.title(), job.company(), job.description(), generationFacts, generationRequirements, List.of());
        String sourceKey = generationCacheKey(jobId, profile, evaluation);
        String idempotencyHash = isBlank(idempotencyKey)
                ? null : idempotencyHash(profile.profileId(), jobId, regeneration, idempotencyKey);
        String cacheKey = regeneration
                ? hash(sourceKey + "|regenerate|" + Objects.toString(idempotencyHash, UUID.randomUUID().toString()))
                : sourceKey;

        GenerationContext context = beginGeneration(profile.profileId(), jobId, profile, evaluation, cacheKey,
                idempotencyHash, reason, regeneration, replaceUserEdited);
        if (context.cached()) {
            metrics.cacheHit();
            return responseMapper.detail(requireOwned(context.packageId()), requireRevision(context.revisionId()));
        }

        long generationStarted = System.nanoTime();
        var storedArtifactKeys = new ArrayList<String>();
        try {
            TailoringPlan plan = boundedPlan(request, generationFacts, requirementIds);
            setRevisionStatus(context.revisionId(), RevisionStatus.GENERATING);
            GeneratedApplicationContent output = null;
            GenerationValidationResult validation = null;
            List<String> repairCodes = List.of();
            int attempts = generationProperties.maxRetries() > 0 ? 2 : 1;
            for (int attempt = 0; attempt < attempts; attempt++) {
                try {
                    output = attempt == 0
                            ? generator.generate(request, plan)
                            : generator.repair(request, plan, repairCodes);
                    output = GeneratedContentNormalizer.normalize(output);
                } catch (RuntimeException invalidOutput) {
                    if (attempt + 1 < attempts && repairableOutputFailure(invalidOutput)) {
                        repairCodes = List.of(safeErrorCode(invalidOutput));
                        repairCodes.forEach(metrics::validationFailed);
                        continue;
                    }
                    if (repairableOutputFailure(invalidOutput)) {
                        repairCodes = List.of(safeErrorCode(invalidOutput));
                        repairCodes.forEach(metrics::validationFailed);
                        output = contentMerger.merge(output, request, plan, jobEvidence);
                        validation = validator.validate(output, generationFacts, jobEvidence);
                        break;
                    }
                    throw invalidOutput;
                }
                validation = validator.validate(output, generationFacts, jobEvidence);
                if (validation.valid()) break;
                validation.codes().forEach(metrics::validationFailed);
                repairCodes = validation.codes();
            }
            if (validation != null && !validation.valid() && output != null) {
                output = contentMerger.merge(output, request, plan, jobEvidence);
                validation = validator.validate(output, generationFacts, jobEvidence);
                if (!validation.valid()) validation.codes().forEach(metrics::validationFailed);
            }
            if (validation == null || !validation.valid()) {
                throw new GenerationRejectedException("CONTENT_PROVENANCE_VALIDATION_FAILED",
                        validation == null ? "Generated content was empty" : String.join(",", validation.codes()));
            }
            setRevisionStatus(context.revisionId(), RevisionStatus.RENDERING);
            List<com.amit.jobagent.application.render.RenderedArtifact> rendered;
            // The external model receives only privacy-safe facts. Rendering is local and must retain the
            // complete immutable resume snapshot (for example, a project bullet carrying removable PDF footer noise).
            try { rendered = renderer.renderAll(toResumeModel(profile, job.title(), job.company(), output, verifiedFacts)); }
            catch (RuntimeException renderingFailure) { metrics.renderingFailed(); throw renderingFailure; }
            for (var artifact : rendered) {
                String key = storageKey(context.packageId(), context.revisionId(), artifact.fileName());
                storage.store(key, artifact.bytes(), artifact.contentType());
                storedArtifactKeys.add(key);
            }
            final GeneratedApplicationContent validOutput = output;
            var result = transactions.execute(status -> persistSuccessfulGeneration(context, validOutput, rendered));
            metrics.succeeded();metrics.duration(System.nanoTime()-generationStarted,true);return result;
        } catch (RuntimeException exception) {
            storedArtifactKeys.forEach(key -> {
                try {
                    storage.delete(key);
                } catch (RuntimeException cleanupFailure) {
                    // Preserve the original failure; incomplete-object cleanup is best effort.
                }
            });
            failGeneration(context, exception);
            metrics.failed();metrics.duration(System.nanoTime()-generationStarted,false);
            throw exception;
        }
    }

    private GenerationContext beginGeneration(
            UUID candidateId, UUID jobId, PublishedProfileSnapshot profile, CompletedEvaluationSnapshot evaluation,
            String cacheKey, String idempotencyHash, String reason, boolean regeneration, boolean replaceUserEdited) {
        try {
            return transactions.execute(status -> {
                var replay = replayContext(cacheKey, idempotencyHash, candidateId);
                if (replay != null) return replay;

                // The immutable candidate row is the cross-job quota mutex. This makes the
                // daily count/check and package creation atomic across concurrent requests.
                activeProfiles.lockForGeneration(candidateId);
                replay = replayContext(cacheKey, idempotencyHash, candidateId);
                if (replay != null) return replay;
                var cached = revisions.findByCacheKey(cacheKey).orElse(null);
                if (cached != null && cached.status == RevisionStatus.FAILED) {
                    cached.retireCacheKey(hash(cacheKey + "|failed|" + cached.id));
                    revisions.saveAndFlush(cached);
                }
                enforceDailyLimit(candidateId);
                var applicationPackage = packages.findForUpdate(candidateId, jobId).orElse(null);
                if (applicationPackage == null) {
                    applicationPackage = packages.saveAndFlush(new ApplicationPackage(candidateId, jobId, actor()));
                }
                if (applicationPackage.status == ApplicationPackageStatus.ARCHIVED) throw new ConflictException("Archived packages cannot be regenerated");
                if (applicationPackage.status == ApplicationPackageStatus.GENERATING) {
                    throw new ConflictException("Another application-package generation is already in progress");
                }
                UUID previousRevision = applicationPackage.currentRevisionId;
                ApplicationPackageStatus previousStatus = applicationPackage.status;
                String previousStaleReason = applicationPackage.staleReason;
                if (previousRevision != null && contents.existsByRevisionIdAndUserEditedTrue(previousRevision)
                        && !replaceUserEdited) {
                    throw new ConflictException("Regeneration would replace user-edited content; explicitly allow replacement");
                }
                int next = Math.toIntExact(revisions.countByPackageId(applicationPackage.getId()) + 1);
                var revision = new ApplicationPackageRevision(applicationPackage.getId(), next, profile.id(), evaluation.id(),
                        evaluation.jobChecksum(), profile.checksum(), evaluation.checksum(), generationProperties.promptVersion(),
                        generationProperties.schemaVersion(), generationProperties.templateVersion(), generationProperties.model(),
                        generationSettingsChecksum(), idempotencyHash, cacheKey);
                revision.status(RevisionStatus.PLANNING);
                revisions.saveAndFlush(revision);
                bindIdempotencyAlias(idempotencyHash, revision.id);
                applicationPackage.generating();
                metrics.requested();
                packages.saveAndFlush(applicationPackage);
                audit.record(regeneration ? AuditEventType.APPLICATION_PACKAGE_REGENERATED : AuditEventType.APPLICATION_PACKAGE_REQUESTED,
                        "ApplicationPackage", applicationPackage.getId(), json(auditMetadata(revision, reason)));
                return new GenerationContext(applicationPackage.getId(), revision.id, previousRevision,
                        previousStatus, previousStaleReason, false);
            });
        } catch (DataIntegrityViolationException race) {
            var replay = replayContext(cacheKey, idempotencyHash, candidateId);
            if (replay == null) throw new ConflictException("An equivalent generation request could not be replayed safely");
            return replay;
        }
    }

    private TailoringPlan boundedPlan(
            ApplicationContentGenerationRequest request, List<VerifiedFactSnapshot> facts, Set<UUID> requirementIds) {
        int attempts = generationProperties.maxRetries() > 0 ? 2 : 1;
        List<String> lastCodes = List.of("INVALID_TAILORING_PLAN");
        for (int attempt = 0; attempt < attempts; attempt++) {
            var plan = generator.plan(request);
            lastCodes = validatePlan(plan, facts, requirementIds);
            if (lastCodes.isEmpty()) return plan;
        }
        throw new GenerationRejectedException("TAILORING_PLAN_VALIDATION_FAILED", String.join(",", lastCodes));
    }

    private static boolean repairableOutputFailure(RuntimeException failure) {
        String message = Objects.toString(failure.getMessage(), "");
        return message.contains("CONTENT_GENERATION_INVALID_RESPONSE")
                || message.contains("CONTENT_GENERATION_SCHEMA_VIOLATION")
                || message.contains("CONTENT_GENERATION_EMPTY_RESPONSE")
                || message.contains("CONTENT_GENERATION_OUTPUT_TRUNCATED");
    }

    private static GeneratedApplicationContent groundedFallback(
            GeneratedApplicationContent generated,
            ApplicationContentGenerationRequest request,
            TailoringPlan plan) {
        var seed = generated == null
                ? new GeneratedApplicationContent(List.of(), List.of(), List.of(), List.of())
                : generated;
        return OllamaApplicationContentGenerator.groundRepair(seed, request, plan);
    }

    private List<String> validatePlan(TailoringPlan plan, List<VerifiedFactSnapshot> facts, Set<UUID> requirementIds) {
        if (plan == null || plan.selectedFactIds() == null || plan.selectedRequirementIds() == null
                || plan.orderedSkills() == null || plan.unsupportedRequirements() == null || plan.omissions() == null) {
            return List.of("MALFORMED_TAILORING_PLAN");
        }
        var codes = new LinkedHashSet<String>();
        Set<UUID> factIds = facts.stream().map(VerifiedFactSnapshot::id).collect(java.util.stream.Collectors.toSet());
        if (!factIds.containsAll(plan.selectedFactIds())) codes.add("UNKNOWN_OR_WRONG_VERSION_FACT_ID");
        if (!requirementIds.containsAll(plan.selectedRequirementIds())) codes.add("UNKNOWN_JOB_REQUIREMENT_ID");
        Set<String> supportedSkills = facts.stream().filter(f -> plan.selectedFactIds().contains(f.id()))
                .flatMap(f -> f.skillTags().stream()).map(ApplicationPackageService::normalize)
                .collect(java.util.stream.Collectors.toSet());
        if (plan.orderedSkills().stream().map(ApplicationPackageService::normalize).anyMatch(s -> !supportedSkills.contains(s))) {
            codes.add("UNSUPPORTED_SKILL_OR_TECHNOLOGY");
        }
        return List.copyOf(codes);
    }

    private PackageDetailResponse persistSuccessfulGeneration(
            GenerationContext context, GeneratedApplicationContent output,
            List<com.amit.jobagent.application.render.RenderedArtifact> rendered) {
        var applicationPackage = packages.findByIdForUpdate(context.packageId())
                .orElseThrow(() -> new IllegalStateException("Application package disappeared during generation"));
        var latest = revisions.findFirstByPackageIdOrderByRevisionNumberDesc(context.packageId())
                .orElseThrow(() -> new IllegalStateException("Application package revision disappeared during generation"));
        if (applicationPackage.status != ApplicationPackageStatus.GENERATING
                || !latest.id.equals(context.revisionId())
                || !Objects.equals(applicationPackage.currentRevisionId, context.previousRevisionId())) {
            throw new ConflictException("Application-package state changed while generation was in progress");
        }
        var revision = requireRevision(context.revisionId());
        if (revision.status != RevisionStatus.RENDERING) {
            throw new ConflictException("Application-package revision is no longer awaiting rendering completion");
        }
        var savedByKey = new LinkedHashMap<String, GeneratedContent>();
        for (var item : output.contents()) {
            ContentOrigin origin = generationProperties.enabled() && !item.key().startsWith("safe-")
                    ? ContentOrigin.AI_GENERATED
                    : ContentOrigin.DETERMINISTIC;
            var saved = contents.save(new GeneratedContent(revision.id, item.type(), item.key(), item.order(), item.text(),
                    origin, ContentVerificationStatus.VERIFIED));
            savedByKey.put(item.key(), saved);
        }
        for (var atom : output.claims()) {
            var content = savedByKey.get(atom.contentKey());
            if (content == null) throw new IllegalStateException("Validated claim refers to missing content");
            var claim = claims.save(new GeneratedClaim(content.getId(), atom.claimText(), atom.claimType(), atom.contentKey(),
                    ClaimValidationStatus.VALID, "[]"));
            for (UUID factId : atom.factIds() == null ? List.<UUID>of() : atom.factIds()) {
                claimSources.save(GeneratedClaimSource.fromSnapshotEvidence(claim.id, factId));
            }
            for (UUID requirementId : atom.requirementIds() == null ? List.<UUID>of() : atom.requirementIds()) {
                claimSources.save(new GeneratedClaimSource(claim.id, null, requirementId, null));
            }
            if (!isBlank(atom.jobFieldReference())) {
                claimSources.save(new GeneratedClaimSource(claim.id, null, null, atom.jobFieldReference()));
            }
        }
        for (var artifact : rendered) {
            String key = storageKey(context.packageId(), revision.id, artifact.fileName());
            artifacts.save(new DocumentArtifact(revision.id, artifact.artifactType(), jobAgentProperties.minio().bucket(), key,
                    artifact.fileName(), artifact.contentType(), artifact.sizeBytes(), artifact.sha256Checksum(), artifact.templateVersion()));
        }
        revision.generationNotes(json(output.warnings() == null ? List.of() : output.warnings()),
                json(output.unsupportedRequirements() == null ? List.of() : output.unsupportedRequirements()));
        revision.ready();
        revisions.saveAndFlush(revision);
        applicationPackage.ready(revision.id);
        packages.saveAndFlush(applicationPackage);
        audit.record(AuditEventType.APPLICATION_PACKAGE_COMPLETED, "ApplicationPackage", applicationPackage.getId(),
                json(Map.of("revisionId", revision.id.toString(), "revisionNumber", revision.revisionNumber)));
        return responseMapper.detail(applicationPackage);
    }

    private void failGeneration(GenerationContext context, RuntimeException exception) {
        try {
            transactions.executeWithoutResult(status -> {
                var revision = requireRevision(context.revisionId());
                String code = exception instanceof GenerationRejectedException rejected
                        ? rejected.code : safeErrorCode(exception);
                String safeMessage = exception instanceof GenerationRejectedException
                        ? exception.getMessage()
                        : safeErrorMessage(exception);
                revision.fail(code, limit(safeMessage, 500));
                revisions.save(revision);
                var applicationPackage = packages.findByIdForUpdate(context.packageId())
                        .orElseThrow(() -> new IllegalStateException("Application package disappeared during generation"));
                var latest = revisions.findFirstByPackageIdOrderByRevisionNumberDesc(context.packageId()).orElse(null);
                if (applicationPackage.status == ApplicationPackageStatus.GENERATING
                        && latest != null && latest.id.equals(context.revisionId())
                        && Objects.equals(applicationPackage.currentRevisionId, context.previousRevisionId())) {
                    if (context.previousRevisionId() == null) applicationPackage.fail();
                    else applicationPackage.restore(context.previousRevisionId(), context.previousPackageStatus(),
                            context.previousStaleReason());
                    packages.saveAndFlush(applicationPackage);
                }
                audit.record(AuditEventType.APPLICATION_PACKAGE_FAILED, "ApplicationPackage", applicationPackage.getId(),
                        json(Map.of("revisionId", revision.id.toString(), "errorCode", code)));
            });
        } catch (RuntimeException ignored) {
            // Preserve the original sanitized generation failure.
        }
    }

    private void setRevisionStatus(UUID revisionId, RevisionStatus newStatus) {
        transactions.executeWithoutResult(status -> {
            var revision = requireRevision(revisionId);
            revision.status(newStatus);
            revisions.saveAndFlush(revision);
        });
    }

    private ApplicationPackage refreshStaleness(ApplicationPackage applicationPackage) {
        if (applicationPackage.currentRevisionId == null || applicationPackage.status == ApplicationPackageStatus.ARCHIVED
                || applicationPackage.status == ApplicationPackageStatus.FAILED || applicationPackage.status == ApplicationPackageStatus.GENERATING) {
            return applicationPackage;
        }
        String reason = staleReason(applicationPackage);
        if (reason == null || (applicationPackage.status == ApplicationPackageStatus.STALE && reason.equals(applicationPackage.staleReason))) {
            return applicationPackage;
        }
        return transactions.execute(status -> {
            var current = packages.findById(applicationPackage.getId()).orElseThrow();
            current.stale(reason);
            var revision = requireRevision(current.currentRevisionId);
            if (revision.status == RevisionStatus.READY) revision.status(RevisionStatus.STALE);
            revisions.save(revision);
            return packages.saveAndFlush(current);
        });
    }

    private String staleReason(ApplicationPackage applicationPackage) {
        try {
            var revision = requireRevision(applicationPackage.currentRevisionId);
            var job = jobs.require(applicationPackage.jobId);
            if (!ELIGIBLE_JOB_STATUSES.contains(job.status())) return "JOB_IS_NO_LONGER_ACTIVE";
            if (!Objects.equals(job.contentHash(), revision.jobChecksum)) return "JOB_CONTENT_CHANGED";
            var evaluation = evaluations.require(revision.evaluationId, applicationPackage.jobId);
            if (evaluation.stale()) return "SOURCE_EVALUATION_IS_STALE";
            var latest = evaluations.requireLatest(applicationPackage.jobId);
            if (!latest.id().equals(revision.evaluationId)) return "A_NEWER_EVALUATION_EXISTS";
            if (!profiles.requireActive().id().equals(revision.profileVersionId)) return "ACTIVE_PROFILE_VERSION_CHANGED";
            if (!generationSettingsChecksum().equals(revision.settingsChecksum)) return "GENERATION_POLICY_CHANGED";
            return null;
        } catch (RuntimeException missingSource) {
            return "REQUIRED_SOURCE_UNAVAILABLE";
        }
    }

    private void validateAutomationEligibility(CompletedEvaluationSnapshot evaluation) {
        if (!generationProperties.automationEnabled()) throw new DomainValidationException("Automated package generation is disabled");
        if (evaluation.recommendation() != Recommendation.STRONG_APPLY && evaluation.recommendation() != Recommendation.APPLY) {
            throw new DomainValidationException("Automated generation requires an APPLY recommendation");
        }
        if (evaluation.stale()) throw new DomainValidationException("Automated generation cannot use a stale evaluation");
    }

    private void enforceDailyLimit(UUID candidateId) {
        if (!dailyLimitReached()) return;
        metrics.quotaRejected();
        audit.record(AuditEventType.AI_BUDGET_REJECTED, "CandidateProfile", candidateId,
                json(Map.of("reason", "CONTENT_GENERATION_DAILY_LIMIT")));
        throw new DomainValidationException("The daily application-generation limit has been reached");
    }

    private boolean dailyLimitReached() {
        return revisions.countByCreatedAtGreaterThanEqual(
                LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC)) >= generationProperties.dailyLimit();
    }

    private ApplicationPackage requireOwned(UUID packageId) {
        UUID candidateId = activeProfiles.requireProfileId();
        return packages.findById(packageId).filter(p -> p.candidateId.equals(candidateId))
                .orElseThrow(() -> new ResourceNotFoundException("Application package was not found"));
    }

    private ApplicationPackage requireOwnedForUpdate(UUID packageId) {
        UUID candidateId = activeProfiles.requireProfileId();
        return packages.findByIdForUpdate(packageId).filter(p -> p.candidateId.equals(candidateId))
                .orElseThrow(() -> new ResourceNotFoundException("Application package was not found"));
    }

    private GenerationContext replayContext(String cacheKey, String idempotencyHash, UUID candidateId) {
        if (idempotencyHash != null) {
            var idempotent = idempotentRevision(idempotencyHash);
            if (idempotent != null) {
                var replay = idempotentReplayContext(idempotent, candidateId);
                bindIdempotencyAlias(idempotencyHash, idempotent.id);
                return replay;
            }
        }
        var cached = revisions.findByCacheKey(cacheKey).orElse(null);
        var replay = reusableSourceContext(cached, candidateId);
        if (replay != null) bindIdempotencyAlias(idempotencyHash, cached.id);
        return replay;
    }

    private GenerationContext idempotentReplayContext(ApplicationPackageRevision revision, UUID candidateId) {
        var applicationPackage = ownedCachedPackage(revision, candidateId);
        return new GenerationContext(applicationPackage.getId(), revision.id, applicationPackage.currentRevisionId,
                applicationPackage.status, applicationPackage.staleReason, true);
    }

    private GenerationContext reusableSourceContext(ApplicationPackageRevision revision, UUID candidateId) {
        if (revision == null || revision.status == RevisionStatus.FAILED) {
            return null;
        }
        var applicationPackage = ownedCachedPackage(revision, candidateId);
        if (applicationPackage.status == ApplicationPackageStatus.ARCHIVED) {
            throw new ConflictException("Archived packages cannot be regenerated");
        }
        return new GenerationContext(applicationPackage.getId(), revision.id, applicationPackage.currentRevisionId,
                applicationPackage.status, applicationPackage.staleReason, true);
    }

    private ApplicationPackage ownedCachedPackage(ApplicationPackageRevision revision, UUID candidateId) {
        var applicationPackage = packages.findById(revision.packageId)
                .orElseThrow(() -> new IllegalStateException("Cached package revision has no package"));
        if (!applicationPackage.candidateId.equals(candidateId)) {
            throw new ConflictException("Generation cache belongs to another candidate");
        }
        return applicationPackage;
    }

    private ApplicationPackageRevision idempotentRevision(String idempotencyHash) {
        var alias = idempotencyAliases.findById(idempotencyHash).orElse(null);
        if (alias != null) {
            return revisions.findById(alias.revisionId)
                    .orElseThrow(() -> new IllegalStateException("Idempotency alias has no package revision"));
        }
        return revisions.findByIdempotencyKeyHash(idempotencyHash).orElse(null);
    }

    private void bindIdempotencyAlias(String idempotencyHash, UUID revisionId) {
        if (idempotencyHash == null) return;
        var existing = idempotencyAliases.findById(idempotencyHash).orElse(null);
        if (existing != null) {
            if (!existing.revisionId.equals(revisionId)) {
                throw new ConflictException("Idempotency key is already bound to another package revision");
            }
            return;
        }
        idempotencyAliases.saveAndFlush(new ApplicationPackageIdempotencyAlias(idempotencyHash, revisionId));
    }

    private ApplicationPackageRevision currentRevision(ApplicationPackage applicationPackage) {
        if (applicationPackage.currentRevisionId == null) throw new ResourceNotFoundException("The package has no current revision");
        return requireRevision(applicationPackage.currentRevisionId);
    }

    private ApplicationPackageRevision requireRevision(UUID revisionId) {
        return revisions.findById(revisionId).orElseThrow(() -> new ResourceNotFoundException("Package revision was not found"));
    }

    private Map<UUID, List<GeneratedClaimSource>> sourcesByClaim(List<GeneratedClaim> storedClaims) {
        var ids = storedClaims.stream().map(c -> c.id).toList();
        var result = new LinkedHashMap<UUID, List<GeneratedClaimSource>>();
        if (!ids.isEmpty()) claimSources.findByClaimIdIn(ids)
                .forEach(source -> result.computeIfAbsent(source.claimId, key -> new ArrayList<>()).add(source));
        return result;
    }

    private ResumeDocumentModel toResumeModel(
            PublishedProfileSnapshot profile, String role, String company,
            GeneratedApplicationContent output, List<VerifiedFactSnapshot> facts) {
        JsonNode core = profile.snapshot().path("profile");
        var links = new ArrayList<String>();
        for (String field : List.of("linkedinUrl", "githubUrl", "leetcodeUrl", "portfolioUrl")) {
            String value = text(core, field);
            if (!isBlank(value)) links.add(value);
        }
        var contact = new ResumeContact(textRequired(core, "fullName"), textRequired(core, "email"),
                text(core, "phone"), text(core, "currentLocation"), links);
        String headline = output.contents().stream().filter(c -> c.type() == GeneratedContentType.RESUME_HEADLINE)
                .map(GeneratedContentItem::text).findFirst().orElse(text(core, "professionalTitle"));
        boolean groundedFallback = output.warnings().contains("MODEL_GROUNDED_FALLBACK");
        List<String> sourceSummary = factStatements(facts, "OTHER");
        List<String> generatedSummary = output.contents().stream()
                .filter(c -> c.type() == GeneratedContentType.PROFESSIONAL_SUMMARY)
                .map(GeneratedContentItem::text).map(ApplicationPackageService::cleanResumeText)
                .filter(s -> !s.isBlank()).toList();
        List<String> summary = groundedFallback || generatedSummary.isEmpty() ? sourceSummary : generatedSummary;
        List<String> generatedSkills = output.contents().stream()
                .filter(c -> c.type() == GeneratedContentType.SKILL_SECTION)
                .flatMap(c -> Arrays.stream(c.text().split("[;,\\n]"))).map(String::trim)
                .filter(s -> !s.isBlank()).distinct().toList();
        List<String> skills = !groundedFallback && !generatedSkills.isEmpty()
                ? generatedSkills
                : factStatements(facts, "SKILL");
        if (skills.isEmpty()) skills = generatedSkills;
        Map<UUID, String> tailoredByFact = tailoredTextByFact(output);
        return new ResumeDocumentModel(contact, role, company, headline, summary, skills,
                experienceEntries(facts, tailoredByFact),
                projectEntries(facts, tailoredByFact),
                educationEntries(facts, tailoredByFact), LocalDate.now());
    }

    private static List<String> factStatements(List<VerifiedFactSnapshot> facts, String category) {
        return facts.stream().filter(f -> category.equals(f.category()))
                .map(VerifiedFactSnapshot::statement).map(ApplicationPackageService::cleanResumeText)
                .filter(s -> !s.isBlank()).toList();
    }

    private static Map<UUID, String> tailoredTextByFact(GeneratedApplicationContent output) {
        var contentByKey = new LinkedHashMap<String, GeneratedContentItem>();
        output.contents().forEach(item -> contentByKey.put(item.key(), item));
        var result = new LinkedHashMap<UUID, String>();
        for (GeneratedClaimAtom claim : output.claims()) {
            if (claim.claimType() != ClaimType.CANDIDATE_FACT || claim.factIds() == null || claim.factIds().size() != 1) continue;
            GeneratedContentItem content = contentByKey.get(claim.contentKey());
            if (content == null || !(content.type() == GeneratedContentType.EXPERIENCE_BULLET
                    || content.type() == GeneratedContentType.PROJECT_BULLET
                    || content.type() == GeneratedContentType.EDUCATION_SECTION)) continue;
            String text = cleanResumeText(content.text());
            if (!text.isBlank()) result.putIfAbsent(claim.factIds().getFirst(), text);
        }
        return result;
    }

    private static List<ResumeEntry> experienceEntries(
            List<VerifiedFactSnapshot> facts, Map<UUID, String> tailoredByFact) {
        var entries = new LinkedHashMap<String, ResumeEntryBuilder>();
        String currentCompany = null;
        for (VerifiedFactSnapshot fact : facts) {
            if (!"EMPLOYMENT".equals(fact.category())) continue;
            String company = cleanResumeText(fact.company());
            if (!company.isBlank()) currentCompany = company;
            if (currentCompany == null) continue;
            var entry = entries.computeIfAbsent(currentCompany, ResumeEntryBuilder::new);
            String original = cleanResumeText(fact.statement());
            String value = cleanResumeText(tailoredByFact.getOrDefault(fact.id(), original));
            if (employerHeader(original, currentCompany)) {
                if (entry.dateRange == null) entry.dateRange = dateRangeFromText(original, currentCompany);
            } else if (entry.title == null && looksLikeRoleTitle(original)) {
                entry.title = original;
            } else if (!value.isBlank()) {
                entry.bullets.add(value);
            }
        }
        return entries.values().stream().map(ResumeEntryBuilder::build).toList();
    }

    private static List<ResumeEntry> projectEntries(
            List<VerifiedFactSnapshot> facts, Map<UUID, String> tailoredByFact) {
        var entries = new ArrayList<ResumeEntry>();
        String title = null;
        var bullets = new ArrayList<String>();
        for (VerifiedFactSnapshot fact : facts) {
            if (!"PROJECT".equals(fact.category())) continue;
            String original = cleanResumeText(fact.statement());
            String value = cleanResumeText(tailoredByFact.getOrDefault(fact.id(), original));
            if (title == null && original.length() <= 100 && !original.endsWith(".")) title = original;
            else if (!value.isBlank()) bullets.add(value);
        }
        if (title != null || !bullets.isEmpty()) entries.add(new ResumeEntry(
                title == null ? "Selected Project" : title, null, null, null, bullets));
        return List.copyOf(entries);
    }

    private static List<ResumeEntry> educationEntries(
            List<VerifiedFactSnapshot> facts, Map<UUID, String> tailoredByFact) {
        String institution = null;
        String degree = null;
        String dates = null;
        var bullets = new ArrayList<String>();
        for (VerifiedFactSnapshot fact : facts) {
            if (!"EDUCATION".equals(fact.category())) continue;
            String original = cleanResumeText(fact.statement());
            String value = cleanResumeText(tailoredByFact.getOrDefault(fact.id(), original));
            if (institution == null) {
                dates = dateRangeFromText(original, "");
                String heading = DATE_RANGE_IN_TEXT.matcher(value).replaceFirst("").trim().replaceAll("[,.]$", "");
                int separator = heading.indexOf(',');
                institution = separator > 0 ? heading.substring(0, separator).trim() : heading;
                degree = separator > 0 ? heading.substring(separator + 1).trim() : "Education";
            } else if (!value.isBlank()) bullets.add(value);
        }
        return institution == null ? List.of() : List.of(new ResumeEntry(degree, institution, null, dates, bullets));
    }

    private static boolean employerHeader(String statement, String company) {
        String lower = statement.toLowerCase(Locale.ROOT);
        return lower.startsWith(company.toLowerCase(Locale.ROOT))
                && (DATE_RANGE_IN_TEXT.matcher(statement).find() || lower.contains("present"));
    }

    private static boolean looksLikeRoleTitle(String statement) {
        return statement.length() <= 100 && !statement.contains(".")
                && statement.matches("(?iu).*(engineer|developer|architect|analyst|manager|lead|consultant|intern|specialist).*");
    }

    private static String dateRangeFromText(String statement, String prefix) {
        var matcher = DATE_RANGE_IN_TEXT.matcher(statement);
        if (matcher.find()) return matcher.group(1).replace('\u2013', '-').replace('\u2014', '-');
        String remainder = prefix.isBlank() ? statement : statement.substring(Math.min(prefix.length(), statement.length())).trim();
        return remainder.isBlank() ? null : remainder;
    }

    private static String cleanResumeText(String value) {
        if (value == null) return "";
        return value.replaceFirst("(?iu)\\s+(?:mailto:|tel:|https?://).*", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static final class ResumeEntryBuilder {
        private final String company;
        private String title;
        private String dateRange;
        private final List<String> bullets = new ArrayList<>();

        private ResumeEntryBuilder(String company) { this.company = company; }

        private ResumeEntry build() {
            return new ResumeEntry(title == null ? "Software Engineer" : title, company, null, dateRange, bullets);
        }
    }

    private String generationCacheKey(
            UUID jobId, PublishedProfileSnapshot profile, CompletedEvaluationSnapshot evaluation) {
        return hash(String.join("|", profile.profileId().toString(), jobId.toString(), evaluation.jobChecksum(),
                profile.id().toString(), profile.checksum(), evaluation.id().toString(), evaluation.checksum(),
                generationSettingsChecksum()));
    }

    private String generationSettingsChecksum() {
        return hash(String.join("|",
                generationProperties.promptVersion(), promptCatalog.promptChecksum,
                generationProperties.schemaVersion(), promptCatalog.schemaChecksum,
                String.valueOf(generationProperties.enabled()),
                generationProperties.provider(),
                generationProperties.model(), generationProperties.reasoningEffort(),
                String.valueOf(generationProperties.timeout().toMillis()),
                String.valueOf(generationProperties.maxRetries()),
                String.valueOf(generationProperties.maxInputTokens()), String.valueOf(generationProperties.maxOutputTokens()),
                String.valueOf(generationProperties.maxInputCharacters()),
                generationProperties.templateVersion()));
    }

    private static JobClaimEvidence exactJobEvidence(
            JobMatchingView job,
            List<EvaluationRequirementProvider.RequirementView> requirementViews) {
        var requirementEvidence = new LinkedHashMap<UUID, String>();
        for (var requirement : requirementViews) {
            requirementEvidence.put(
                    requirement.id(),
                    String.join(" ",
                            Objects.toString(requirement.text(), ""),
                            Objects.toString(requirement.jobEvidence(), "")));
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("job.title", job.title());
        fields.put("job.company", job.company());
        fields.put("job.location", job.location());
        fields.put("job.description", job.description());
        return new JobClaimEvidence(requirementEvidence, fields);
    }

    static String cacheKeyForTest(String... values) { return hash(String.join("|", values)); }

    private int nextContentOrder(UUID revisionId) {
        return contents.findByRevisionIdOrderByOrderAscIdAsc(revisionId).stream().mapToInt(c -> c.order).max().orElse(-1) + 1;
    }

    private Map<String, Object> auditMetadata(ApplicationPackageRevision revision, String reason) {
        var result = new LinkedHashMap<String, Object>();
        result.put("revisionId", revision.id.toString());
        result.put("revisionNumber", revision.revisionNumber);
        if (!isBlank(reason)) result.put("overrideReason", limit(reason.trim(), 500));
        return result;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { return "{}"; }
    }

    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || isBlank(authentication.getName()) ? "system" : limit(authentication.getName(), 150);
    }

    private static String safeErrorCode(RuntimeException exception) {
        return ApplicationGenerationFailure.code(exception);
    }

    private static String safeErrorMessage(RuntimeException exception) {
        if (exception instanceof GenerationRejectedException) return "Generated content did not pass deterministic validation";
        if (exception instanceof DomainValidationException || exception instanceof ConflictException) return exception.getMessage();
        return ApplicationGenerationFailure.message(exception);
    }

    private static String storageKey(UUID packageId, UUID revisionId, String fileName) {
        return "application-packages/" + packageId + "/revisions/" + revisionId + "/" + fileName;
    }

    private static String dateRange(String start, String end) {
        if (isBlank(start) && isBlank(end)) return null;
        return (isBlank(start) ? "Unknown" : start) + " - " + (isBlank(end) ? "Present" : end);
    }

    private static String text(JsonNode node, String field) {
        var value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private static String textRequired(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) throw new DomainValidationException("Published profile is missing required contact information");
        return value;
    }

    private static String normalize(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String idempotencyHash(
            UUID candidateId, UUID jobId, boolean regeneration, String idempotencyKey) {
        return hash(String.join("|", candidateId.toString(), jobId.toString(),
                regeneration ? "regenerate" : "create", idempotencyKey.trim()));
    }

    private static String limit(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    private static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private record GenerationContext(
            UUID packageId,
            UUID revisionId,
            UUID previousRevisionId,
            ApplicationPackageStatus previousPackageStatus,
            String previousStaleReason,
            boolean cached) {}

    private static final class GenerationRejectedException extends DomainValidationException {
        private final String code;
        private GenerationRejectedException(String code, String details) {
            super("Generated content failed deterministic validation: " + limit(details, 300));
            this.code = code;
        }
    }
}
