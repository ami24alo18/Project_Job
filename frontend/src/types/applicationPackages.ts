import type { PagedResponse } from './jobs'

export type ApplicationPackageStatus = 'REQUESTED' | 'GENERATING' | 'READY' | 'FAILED' | 'STALE' | 'ARCHIVED'
export type ApplicationPackageRevisionStatus = 'REQUESTED' | 'PLANNING' | 'GENERATING' | 'VALIDATING' | 'RENDERING' | 'READY' | 'FAILED' | 'STALE'
export type GeneratedContentType =
  | 'RESUME_HEADLINE'
  | 'PROFESSIONAL_SUMMARY'
  | 'SKILL_SECTION'
  | 'EXPERIENCE_BULLET'
  | 'PROJECT_BULLET'
  | 'EDUCATION_SECTION'
  | 'COVER_LETTER'
  | 'RECRUITER_MESSAGE'
  | 'APPLICATION_ANSWER'
export type GeneratedContentOrigin = 'DETERMINISTIC' | 'AI_GENERATED' | 'USER_EDITED'
export type GeneratedContentVerificationStatus = 'VERIFIED' | 'PARTIALLY_VERIFIED' | 'UNVERIFIED' | 'REJECTED'
export type GeneratedClaimType = 'CANDIDATE_FACT' | 'JOB_REFERENCE' | 'NON_FACTUAL'
export type ClaimValidationStatus = 'VALID' | 'INVALID' | 'REQUIRES_REVIEW'
export type ApplicationQuestionClassification =
  | 'VERIFIED_AUTOMATIC'
  | 'SUGGESTED_REQUIRES_REVIEW'
  | 'USER_INPUT_REQUIRED'
  | 'SENSITIVE_NEVER_AUTOMATIC'
export type ApplicationQuestionAnswerStatus = 'DRAFTED' | 'USER_INPUT_REQUIRED' | 'BLOCKED_SENSITIVE' | 'UNANSWERED' | 'USER_EDITED'
export type DocumentArtifactType = 'HTML_PREVIEW' | 'PDF_RESUME' | 'DOCX_RESUME'
export type ReviewStatus = 'NOT_READY' | 'PENDING_REVIEW' | 'CHANGES_REQUESTED' | 'APPROVED_FOR_HANDOFF' | 'REJECTED' | 'INVALIDATED'
export type ReviewDecisionType = 'APPROVED_FOR_HANDOFF' | 'CHANGES_REQUESTED' | 'REJECTED'

export interface ApplicationPackageSummary {
  id: string
  jobId: string
  jobTitle: string
  company: string
  recommendation?: string
  status: ApplicationPackageStatus
  currentRevisionNumber?: number
  createdAt: string
  updatedAt: string
  stale: boolean
  staleReason?: string
  artifactTypes: DocumentArtifactType[]
}

export interface GeneratedContent {
  id: string
  packageRevisionId: string
  contentType: GeneratedContentType
  contentKey: string
  sectionOrder: number
  text: string
  origin: GeneratedContentOrigin
  verificationStatus: GeneratedContentVerificationStatus
  userEdited: boolean
  createdAt: string
  updatedAt: string
  recordVersion: number
}

export interface GeneratedClaimSource {
  candidateFactId?: string
  jobRequirementId?: string
  jobFieldReference?: string
}

export interface GeneratedClaim {
  id: string
  generatedContentId: string
  claimText: string
  claimType: GeneratedClaimType
  contentPath: string
  validationStatus: ClaimValidationStatus
  validationCodes: string[]
  sources: GeneratedClaimSource[]
  createdAt: string
}

export interface ApplicationQuestionDraft {
  id: string
  packageRevisionId: string
  question: string
  questionHash: string
  normalizedQuestionKey: string
  classification: ApplicationQuestionClassification
  draftAnswer?: string
  answerStatus: ApplicationQuestionAnswerStatus
  confidence?: number
  createdAt: string
  updatedAt: string
}

export interface DocumentArtifact {
  id: string
  packageRevisionId: string
  artifactType: DocumentArtifactType
  contentType: string
  sizeBytes: number
  sha256Checksum: string
  templateVersion: string
  renderingStatus: 'PENDING' | 'READY' | 'FAILED'
  createdAt: string
  fileName?: string
}

export interface GenerationWarning {
  code?: string
  message: string
}

export interface UnsupportedRequirement {
  requirementId?: string
  requirement?: string
  reason?: string
}

export interface ApplicationPackageRevision {
  id: string
  packageId: string
  revisionNumber: number
  profileVersionId: string
  evaluationId: string
  status: ApplicationPackageRevisionStatus
  reviewStatus?: ReviewStatus
  reviewRecordVersion?: number
  sourceJobChecksum: string
  sourceProfileChecksum: string
  sourceEvaluationChecksum: string
  generationPromptVersion: string
  generationSchemaVersion: string
  templateVersion: string
  model: string
  failureCode?: string
  failureMessage?: string
  createdAt: string
  completedAt?: string
  contents: GeneratedContent[]
  claims: GeneratedClaim[]
  questions: ApplicationQuestionDraft[]
  artifacts: DocumentArtifact[]
  warnings?: Array<GenerationWarning | string>
  unsupportedRequirements?: Array<UnsupportedRequirement | string>
}

export interface ApplicationPackageDetail extends ApplicationPackageSummary {
  createdBy: string
  recordVersion?: number
  currentRevision?: ApplicationPackageRevision
}

export interface PackageListFilters {
  status?: ApplicationPackageStatus
  stale?: boolean
  page?: number
  size?: number
  sort?: string
}

export interface ContentUpdate {
  text: string
  recordVersion: number
}

export interface RegenerationRequest {
  replaceUserEdited: boolean
  reason?: string
}

export interface ValidationResult {
  valid: boolean
  validationCodes: string[]
  warnings?: string[]
}

export type ApplicationPackagePage = PagedResponse<ApplicationPackageSummary>

export interface ReviewChecklist { resumeReviewed:boolean; generatedContentReviewed:boolean; factWarningsAcknowledged:boolean; applicationQuestionsReviewed:boolean; artifactsReviewed:boolean; accuracyConfirmed:boolean }
export interface BlockingReason { code:string; message:string }
export interface HandoffEligibility { packageId:string; revisionId:string; eligible:boolean; reviewStatus:ReviewStatus; reviewRecordVersion:number; blockingReasons:BlockingReason[]; artifactManifestChecksum:string; validationChecksum:string }
export interface ReviewDecision { id:string; decision:ReviewDecisionType; actorId:string; actorRole:string; comment?:string; checklist:ReviewChecklist; createdAt:string; invalidatedAt?:string; invalidationReason?:string }
export interface ApplicationReview { revision:ApplicationPackageRevision; reviewStatus:ReviewStatus; reviewRecordVersion:number; handoffEligibility:HandoffEligibility; decisionHistory:ReviewDecision[] }
export interface ReviewDecisionRequest { checklist:ReviewChecklist; comment?:string; reviewRecordVersion:number; questionResolutions:Record<string,'ACKNOWLEDGED'|'COMPLETED'|'INTENTIONALLY_EXCLUDED'> }
export interface ReviewQueueItem { packageId:string; revisionId:string; revisionNumber:number; jobTitle:string; company:string; recommendation?:string; reviewStatus:ReviewStatus; stale:boolean; staleReason?:string; createdAt:string }
export type ReviewQueuePage = PagedResponse<ReviewQueueItem>
export type ApplicationHandoffStatus='CREATED'|'READY_FOR_MANUAL_SUBMISSION'|'OPENED_EXTERNALLY'|'SUBMITTED_REPORTED_BY_USER'|'NOT_SUBMITTED'|'CANCELLED'|'BLOCKED'|'EXPIRED'
export interface ApplicationHandoff { id:string; packageId:string; revisionId:string; revisionNumber:number; jobTitle:string; company:string; source:string; applyUrl:string; deadline?:string; status:ApplicationHandoffStatus; approvalTimestamp:string; createdAt:string; launchedAt?:string; submissionReportedAt?:string; recordVersion:number; eligibility:HandoffEligibility; activity:Array<{id:string;action:string;actor:string;createdAt:string;note?:string;externalReference?:string}> }
export interface SubmissionKit { handoffId:string; jobTitle:string; company:string; applyUrl:string; coverLetter?:string; recruiterMessage?:string; answers:Array<{questionId:string;question:string;answer:string}>; warnings:string[]; checklist:string[]; eligibility:HandoffEligibility }
