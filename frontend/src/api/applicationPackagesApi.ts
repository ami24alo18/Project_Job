import { apiClient } from './apiClient'
import type {
  ApplicationPackageDetail,
  ApplicationPackagePage,
  ApplicationPackageRevision,
  ApplicationPackageSummary,
  ApplicationQuestionDraft,
  ContentUpdate,
  DocumentArtifactType,
  GeneratedContent,
  PackageListFilters,
  RegenerationRequest,
  ValidationResult,
  ApplicationReview,
  ReviewDecisionRequest,
  ReviewQueuePage,
  ApplicationHandoff, SubmissionKit,
} from '../types/applicationPackages'

type RawContent = Partial<GeneratedContent> & { type?: GeneratedContent['contentType']; key?: string; order?: number }
type RawClaim = Partial<import('../types/applicationPackages').GeneratedClaim> & {
  contentId?: string
  candidateFactIds?: string[]
  jobRequirementIds?: string[]
  jobFieldReference?: string
}
type RawQuestion = Partial<ApplicationQuestionDraft>
type RawArtifact = Partial<import('../types/applicationPackages').DocumentArtifact> & { type?: DocumentArtifactType }
type RawRevision = Partial<ApplicationPackageRevision> & {
  promptVersion?: string
  schemaVersion?: string
  contents?: RawContent[]
  claims?: RawClaim[]
  questions?: RawQuestion[]
  artifacts?: RawArtifact[]
}
type RawSummary = Partial<ApplicationPackageSummary>
type RawDetail = RawSummary & Omit<Partial<ApplicationPackageDetail>, 'currentRevision'> & { currentRevision?: RawRevision }
const APPLICATION_GENERATION_TIMEOUT_MS = 180_000

export function createApplicationPackageIdempotencyKey(): string {
  return globalThis.crypto?.randomUUID?.() ?? `application-package-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function artifactPath(type: Exclude<DocumentArtifactType, 'HTML_PREVIEW'>) {
  return type === 'PDF_RESUME' ? 'pdf' : 'docx'
}

function fallbackName(type: Exclude<DocumentArtifactType, 'HTML_PREVIEW'>) {
  return type === 'PDF_RESUME' ? 'tailored-resume.pdf' : 'tailored-resume.docx'
}

function safeDownloadName(header: unknown, fallback: string) {
  if (typeof header !== 'string') return fallback
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header)?.[1]
  const plain = /filename="?([^";]+)"?/i.exec(header)?.[1]
  let candidate: string | undefined
  try { candidate = encoded ? decodeURIComponent(encoded) : plain } catch { candidate = plain }
  if (!candidate) return fallback
  candidate = Array.from(candidate, character => {
    const code = character.charCodeAt(0)
    return code < 32 || code === 127 ? '_' : character
  }).join('').replace(/[<>:"/\\|?*]/g, '_').replace(/^\.+/, '').trim()
  return candidate || fallback
}

function normalizeContent(raw: RawContent, revisionId: string): GeneratedContent {
  return {
    id: raw.id ?? '',
    packageRevisionId: raw.packageRevisionId ?? revisionId,
    contentType: raw.contentType ?? raw.type ?? 'PROFESSIONAL_SUMMARY',
    contentKey: raw.contentKey ?? raw.key ?? '',
    sectionOrder: raw.sectionOrder ?? raw.order ?? 0,
    text: raw.text ?? '',
    origin: raw.origin ?? 'AI_GENERATED',
    verificationStatus: raw.verificationStatus ?? 'UNVERIFIED',
    userEdited: raw.userEdited ?? raw.origin === 'USER_EDITED',
    createdAt: raw.createdAt ?? '',
    updatedAt: raw.updatedAt ?? raw.createdAt ?? '',
    recordVersion: raw.recordVersion ?? 0,
  }
}

function normalizeClaim(raw: RawClaim): import('../types/applicationPackages').GeneratedClaim {
  const sources = raw.sources ?? [
    ...(raw.candidateFactIds ?? []).map(candidateFactId => ({ candidateFactId })),
    ...(raw.jobRequirementIds ?? []).map(jobRequirementId => ({ jobRequirementId })),
    ...(raw.jobFieldReference ? [{ jobFieldReference: raw.jobFieldReference }] : []),
  ]
  return {
    id: raw.id ?? '',
    generatedContentId: raw.generatedContentId ?? raw.contentId ?? '',
    claimText: raw.claimText ?? '',
    claimType: raw.claimType ?? 'NON_FACTUAL',
    contentPath: raw.contentPath ?? '',
    validationStatus: raw.validationStatus ?? 'REQUIRES_REVIEW',
    validationCodes: raw.validationCodes ?? [],
    sources,
    createdAt: raw.createdAt ?? '',
  }
}

function normalizeQuestion(raw: RawQuestion, revisionId: string, createdAt: string): ApplicationQuestionDraft {
  const confidence = raw.confidence === undefined ? undefined : raw.confidence > 1 ? raw.confidence / 100 : raw.confidence
  return {
    id: raw.id ?? '',
    packageRevisionId: raw.packageRevisionId ?? revisionId,
    question: raw.question ?? '',
    questionHash: raw.questionHash ?? '',
    normalizedQuestionKey: raw.normalizedQuestionKey ?? '',
    classification: raw.classification ?? 'USER_INPUT_REQUIRED',
    draftAnswer: raw.draftAnswer,
    answerStatus: raw.answerStatus ?? 'UNANSWERED',
    confidence,
    createdAt: raw.createdAt ?? createdAt,
    updatedAt: raw.updatedAt ?? raw.createdAt ?? createdAt,
  }
}

function normalizeArtifact(raw: RawArtifact, revisionId: string, createdAt: string): import('../types/applicationPackages').DocumentArtifact {
  return {
    id: raw.id ?? '',
    packageRevisionId: raw.packageRevisionId ?? revisionId,
    artifactType: raw.artifactType ?? raw.type ?? 'HTML_PREVIEW',
    contentType: raw.contentType ?? 'application/octet-stream',
    sizeBytes: raw.sizeBytes ?? 0,
    sha256Checksum: raw.sha256Checksum ?? '',
    templateVersion: raw.templateVersion ?? '',
    renderingStatus: raw.renderingStatus ?? 'READY',
    createdAt: raw.createdAt ?? createdAt,
    fileName: raw.fileName,
  }
}

function normalizeRevision(raw: RawRevision): ApplicationPackageRevision {
  const id = raw.id ?? ''
  const createdAt = raw.createdAt ?? ''
  return {
    id,
    packageId: raw.packageId ?? '',
    revisionNumber: raw.revisionNumber ?? 0,
    profileVersionId: raw.profileVersionId ?? '',
    evaluationId: raw.evaluationId ?? '',
    status: raw.status ?? 'REQUESTED',
    reviewStatus: raw.reviewStatus ?? (raw.status === 'READY' ? 'PENDING_REVIEW' : 'NOT_READY'),
    reviewRecordVersion: raw.reviewRecordVersion ?? 0,
    sourceJobChecksum: raw.sourceJobChecksum ?? '',
    sourceProfileChecksum: raw.sourceProfileChecksum ?? '',
    sourceEvaluationChecksum: raw.sourceEvaluationChecksum ?? '',
    generationPromptVersion: raw.generationPromptVersion ?? raw.promptVersion ?? '',
    generationSchemaVersion: raw.generationSchemaVersion ?? raw.schemaVersion ?? '',
    templateVersion: raw.templateVersion ?? '',
    model: raw.model ?? '',
    failureCode: raw.failureCode,
    failureMessage: raw.failureMessage,
    createdAt,
    completedAt: raw.completedAt,
    contents: (raw.contents ?? []).map(content => normalizeContent(content, id)),
    claims: (raw.claims ?? []).map(normalizeClaim),
    questions: (raw.questions ?? []).map(question => normalizeQuestion(question, id, createdAt)),
    artifacts: (raw.artifacts ?? []).map(artifact => normalizeArtifact(artifact, id, createdAt)),
    warnings: raw.warnings ?? [],
    unsupportedRequirements: raw.unsupportedRequirements ?? [],
  }
}

function normalizeSummary(raw: RawSummary): ApplicationPackageSummary {
  return {
    id: raw.id ?? '',
    jobId: raw.jobId ?? '',
    jobTitle: raw.jobTitle ?? 'Untitled job',
    company: raw.company ?? 'Unknown company',
    recommendation: raw.recommendation,
    status: raw.status ?? 'REQUESTED',
    currentRevisionNumber: raw.currentRevisionNumber,
    createdAt: raw.createdAt ?? '',
    updatedAt: raw.updatedAt ?? raw.createdAt ?? '',
    stale: raw.stale ?? raw.status === 'STALE',
    staleReason: raw.staleReason,
    artifactTypes: raw.artifactTypes ?? [],
  }
}

function normalizeDetail(raw: RawDetail): ApplicationPackageDetail {
  const currentRevision = raw.currentRevision ? normalizeRevision(raw.currentRevision) : undefined
  return {
    ...normalizeSummary({ ...raw, currentRevisionNumber: raw.currentRevisionNumber ?? currentRevision?.revisionNumber, artifactTypes: raw.artifactTypes ?? currentRevision?.artifacts.map(artifact => artifact.artifactType) }),
    createdBy: raw.createdBy ?? 'unknown',
    recordVersion: raw.recordVersion,
    currentRevision,
  }
}

function normalizePage(raw: ApplicationPackagePage | RawSummary[]): ApplicationPackagePage {
  if (Array.isArray(raw)) return { content: raw.map(normalizeSummary), page: 0, size: raw.length, totalElements: raw.length, totalPages: raw.length ? 1 : 0 }
  return { ...raw, content: (raw.content ?? []).map(normalizeSummary) }
}

export const applicationPackagesApi = {
  createHandoff: async(packageId:string,revisionId:string,key=createApplicationPackageIdempotencyKey()) => (await apiClient.post<ApplicationHandoff>(`/api/v1/application-packages/${packageId}/revisions/${revisionId}/handoffs`,{}, {headers:{'Idempotency-Key':key}})).data,
  handoffs: async(page=0,size=20) => (await apiClient.get<import('../types/jobs').PagedResponse<ApplicationHandoff>>('/api/v1/application-handoffs',{params:{page,size}})).data,
  handoff: async(id:string) => (await apiClient.get<ApplicationHandoff>(`/api/v1/application-handoffs/${id}`)).data,
  submissionKit: async(id:string) => (await apiClient.get<SubmissionKit>(`/api/v1/application-handoffs/${id}/submission-kit`)).data,
  handoffAction: async(id:string,action:'launch'|'report-submitted'|'mark-not-submitted'|'cancel',recordVersion:number,note?:string,externalReference?:string) => (await apiClient.post<ApplicationHandoff|{handoff:ApplicationHandoff}>(`/api/v1/application-handoffs/${id}/${action}`,{recordVersion,note,externalReference},{headers:{'Idempotency-Key':createApplicationPackageIdempotencyKey()}})).data,
  reviewQueue: async (params?: Record<string, unknown>) => (await apiClient.get<ReviewQueuePage>('/api/v1/application-packages/review-queue', { params })).data,
  review: async (packageId:string, revisionId:string) => { const raw=(await apiClient.get<Omit<ApplicationReview,'revision'> & {revision:RawRevision}>(`/api/v1/application-packages/${packageId}/revisions/${revisionId}/review`)).data; return {...raw,revision:normalizeRevision(raw.revision)} },
  decide: async (packageId:string,revisionId:string,action:'approve'|'request-changes'|'reject',request:ReviewDecisionRequest) => { const raw=(await apiClient.post<Omit<ApplicationReview,'revision'> & {revision:RawRevision}>(`/api/v1/application-packages/${packageId}/revisions/${revisionId}/review/${action}`,request)).data; return {...raw,revision:normalizeRevision(raw.revision)} },
  list: async (params?: PackageListFilters) => normalizePage((await apiClient.get<ApplicationPackagePage | RawSummary[]>('/api/v1/application-packages', { params })).data),
  get: async (packageId: string) => normalizeDetail((await apiClient.get<RawDetail>(`/api/v1/application-packages/${packageId}`)).data),
  revisions: async (packageId: string) => (await apiClient.get<RawRevision[]>(`/api/v1/application-packages/${packageId}/revisions`)).data.map(normalizeRevision),
  revision: async (packageId: string, revisionId: string) => normalizeRevision((await apiClient.get<RawRevision>(`/api/v1/application-packages/${packageId}/revisions/${revisionId}`)).data),
  generate: async (jobId: string, key = createApplicationPackageIdempotencyKey()) =>
    normalizeDetail((await apiClient.post<RawDetail>(`/api/v1/jobs/${jobId}/application-packages`, {}, { headers: { 'Idempotency-Key': key }, timeout: APPLICATION_GENERATION_TIMEOUT_MS })).data),
  regenerate: async (packageId: string, request: RegenerationRequest, key = createApplicationPackageIdempotencyKey()) =>
    normalizeDetail((await apiClient.post<RawDetail>(`/api/v1/application-packages/${packageId}/regenerate`, request, { headers: { 'Idempotency-Key': key }, timeout: APPLICATION_GENERATION_TIMEOUT_MS })).data),
  updateContent: async (packageId: string, contentId: string, request: ContentUpdate) =>
    normalizeContent((await apiClient.put<RawContent>(`/api/v1/application-packages/${packageId}/content/${contentId}`, request)).data, ''),
  validate: async (packageId: string) =>
    (await apiClient.post<ValidationResult>(`/api/v1/application-packages/${packageId}/validate`)).data,
  addQuestions: async (packageId: string, questions: string[]) =>
    (await apiClient.post<RawQuestion[]>(`/api/v1/application-packages/${packageId}/questions`, { questions })).data.map(question => normalizeQuestion(question, '', '')),
  draftQuestions: async (packageId: string) =>
    (await apiClient.post<RawQuestion[]>(`/api/v1/application-packages/${packageId}/questions/draft`)).data.map(question => normalizeQuestion(question, '', '')),
  preview: async (packageId: string) =>
    (await apiClient.get<string>(`/api/v1/application-packages/${packageId}/resume/preview`, { responseType: 'text', headers: { Accept: 'text/html' } })).data,
  revisionPreview: async (packageId:string,revisionId:string) => (await apiClient.get<string>(`/api/v1/application-packages/${packageId}/revisions/${revisionId}/resume/preview`,{responseType:'text',headers:{Accept:'text/html'}})).data,
  revisionDownload: async (packageId:string,revisionId:string,type:Exclude<DocumentArtifactType,'HTML_PREVIEW'>) => {
    const path=artifactPath(type),accept=type==='PDF_RESUME'?'application/pdf':'application/vnd.openxmlformats-officedocument.wordprocessingml.document';const response=await apiClient.get<Blob>(`/api/v1/application-packages/${packageId}/revisions/${revisionId}/resume/${path}`,{responseType:'blob',headers:{Accept:accept}});const blob=response.data instanceof Blob?response.data:new Blob([response.data],{type:accept});const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=safeDownloadName(response.headers['content-disposition'],fallbackName(type));document.body.appendChild(a);a.click();a.remove();URL.revokeObjectURL(url)
  },
  download: async (packageId: string, type: Exclude<DocumentArtifactType, 'HTML_PREVIEW'>) => {
    const path = artifactPath(type)
    const accept = type === 'PDF_RESUME' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    const response = await apiClient.get<Blob>(`/api/v1/application-packages/${packageId}/resume/${path}`, { responseType: 'blob', headers: { Accept: accept } })
    const blob = response.data instanceof Blob ? response.data : new Blob([response.data], { type: accept })
    const objectUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = safeDownloadName(response.headers['content-disposition'], fallbackName(type))
    anchor.style.display = 'none'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(objectUrl)
  },
  archive: async (packageId: string) =>
    normalizeDetail((await apiClient.post<RawDetail>(`/api/v1/application-packages/${packageId}/archive`)).data),
}
