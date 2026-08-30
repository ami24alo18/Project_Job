export type JobSourceType = 'LEVER' | 'GREENHOUSE' | 'EMAIL_WEBHOOK' | 'EXTERNAL_API' | 'CAREER_SITE' | 'MANUAL'
export type ConfiguredFeedSourceType = 'LEVER' | 'GREENHOUSE' | 'EMAIL_WEBHOOK'
export type SourceRegion = 'DEFAULT' | 'GLOBAL' | 'EU'
export type JobSourceTriggerType = 'MANUAL' | 'N8N' | 'RETRY' | 'WEBHOOK' | 'EXTRACTION_WORKER'
export type JobSourceRunCoverage = 'COMPLETE_INVENTORY' | 'FILTERED_QUERY' | 'PUSH_BATCH'
export type JobSourceRunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL_SUCCESS' | 'FAILED'
export type JobSourceCategory = 'PULL_FEED' | 'PUSH_WEBHOOK' | 'EMAIL_WEBHOOK'
export type JobSourceConnectorType = 'LEVER' | 'GREENHOUSE' | 'EMAIL' | 'JSEARCH' | 'JOBSPY' | 'CUSTOM_WEBHOOK' | 'ORACLE_CX' | 'WORKDAY' | 'SMARTRECRUITERS' | 'GENERIC_JSON_LD' | 'CUSTOM_RECIPE'
export type JobSourceSupportStatus = 'SUPPORTED' | 'NEEDS_AUTHORIZATION' | 'NEEDS_ADAPTER' | 'NEEDS_EXTRACTION_RECIPE' | 'UNSUPPORTED' | 'VALIDATION_FAILED'
export type DatePostedWindow = 'ANY' | 'TODAY' | 'THREE_DAYS' | 'WEEK' | 'MONTH'
export type WorkplaceType = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'UNSPECIFIED'
export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'CONTRACT' | 'TEMPORARY' | 'INTERNSHIP' | 'OTHER' | 'UNSPECIFIED'
export type SalaryInterval = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'OTHER' | 'UNSPECIFIED'
export type JobPostingStatus = 'READY_FOR_EVALUATION' | 'NEEDS_REVIEW' | 'DUPLICATE' | 'EXPIRED' | 'SOURCE_REMOVED' | 'ARCHIVED'

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface JobSourceConfiguration {
  id: string
  displayName: string
  sourceType: Exclude<JobSourceType, 'MANUAL'>
  sourceCategory?: JobSourceCategory
  connectorType?: JobSourceConnectorType
  providerIdentifier: string
  region: SourceRegion
  careerSiteUrl?: string
  canonicalHost?: string
  supportStatus?: JobSourceSupportStatus
  supportMessage?: string
  webhookConfigured?: boolean
  detectionVersion?: string
  extractionRecipeVersion?: string
  lastConnectionTestAt?: string
  lastConnectionTestStatus?: 'SUCCEEDED' | 'FAILED'
  enabled: boolean
  pageSize: number
  maximumPagesPerRun: number
  missingRunThreshold: number
  lastSuccessfulSyncAt?: string
  lastAttemptedSyncAt?: string
  consecutiveFailureCount: number
  recordVersion: number
  createdAt: string
  updatedAt: string
  archivedAt?: string
}

export interface JobSourceInput {
  displayName: string
  sourceType: ConfiguredFeedSourceType
  providerIdentifier: string
  region: SourceRegion
  enabled: boolean
  pageSize: number
  maximumPagesPerRun: number
  missingRunThreshold: number
  recordVersion?: number
}

export interface ExternalJobSourceInput {
  displayName: string
  providerIdentifier: string
  connectorType: 'JSEARCH' | 'JOBSPY' | 'CUSTOM_WEBHOOK'
  enabled: boolean
}

export interface JobSpyPolicy {
  configured: boolean
  allowedSites: string[]
}

export interface ExternalJobSourceCreated {
  source: JobSourceConfiguration
  webhookUrl: string
  webhookToken: string
}

export interface CareerSiteDiscoveryInput {
  companyName: string
  careerSiteUrl: string
}

export interface CareerSiteDiscovery {
  canonicalUrl: string
  canonicalHost: string
  connectorType?: JobSourceConnectorType
  providerIdentifier: string
  supportStatus: JobSourceSupportStatus
  supportMessage: string
  detectionVersion: string
}

export interface CareerSiteJobSourceInput extends CareerSiteDiscoveryInput {
  enabled: boolean
  pageSize: number
  maximumPagesPerRun: number
  missingRunThreshold: number
}

export interface JobSourceConnectionTest {
  source: JobSourceConfiguration
  status: 'SUCCEEDED' | 'FAILED'
  discoveredCount: number
  message: string
}

export interface WebhookTokenRotation {
  sourceId: string
  webhookUrl: string
  webhookToken: string
}

export interface JobSourceSearchRuleInput {
  name: string
  query: string
  locations: string[]
  remoteAllowed: boolean
  hybridAllowed: boolean
  onsiteAllowed: boolean
  datePostedWindow: DatePostedWindow
  maximumResults: number
  enabled: boolean
  recordVersion?: number
}

export interface JobSourceSearchRule extends JobSourceSearchRuleInput {
  id: string
  sourceId: string
  recordVersion: number
  createdAt: string
  updatedAt: string
}

export interface JobSourceRun {
  id: string
  sourceId: string
  triggerType: JobSourceTriggerType
  coverage?: JobSourceRunCoverage
  searchRuleId?: string
  externalEventId?: string
  status: JobSourceRunStatus
  checkpoint?: unknown
  startedAt?: string
  completedAt?: string
  discoveredCount: number
  createdCount: number
  updatedCount: number
  unchangedCount: number
  duplicateCount: number
  failedCount: number
  removedCount: number
  safeErrorCode?: string
  safeErrorMessage?: string
  createdAt: string
  errors?: { id: string; externalId?: string; safeErrorCode: string; safeErrorMessage: string; createdAt: string }[]
}

export interface SyncAccepted {
  runId: string
  status: JobSourceRunStatus
}

export interface JobPosting {
  id: string
  sourceId?: string
  sourceType: JobSourceType
  externalId: string
  duplicateOfJobId?: string
  company: string
  title: string
  location?: string
  countryCode?: string
  workplaceType: WorkplaceType
  employmentType: EmploymentType
  department?: string
  team?: string
  descriptionPlainText?: string
  descriptionTruncated: boolean
  applyUrl?: string
  sourceUrl?: string
  salaryMinimum?: number
  salaryMaximum?: number
  salaryCurrency?: string
  salaryInterval?: SalaryInterval
  publishedAt?: string
  sourceUpdatedAt?: string
  expiresAt?: string
  firstSeenAt: string
  lastSeenAt: string
  missingSuccessfulRunCount: number
  fingerprint: string
  contentHash: string
  status: JobPostingStatus
  manuallyEdited: boolean
  sourceUpdateAvailable?: boolean
  ingestionProvider?: 'LEVER' | 'GREENHOUSE' | 'EMAIL' | 'MANUAL' | 'JSEARCH' | 'JOBSPY' | 'CUSTOM_WEBHOOK' | 'ORACLE_CX' | 'WORKDAY' | 'SMARTRECRUITERS' | 'GENERIC_JSON_LD' | 'CUSTOM_RECIPE'
  originPublisher?: string
  discoveryQuery?: string
  externalEventId?: string
  extractionRecipeVersion?: string
  recordVersion: number
  createdAt: string
  updatedAt: string
}

export interface JobInput {
  company: string
  title: string
  location?: string
  countryCode?: string
  workplaceType: WorkplaceType
  employmentType: EmploymentType
  department?: string
  team?: string
  description?: string
  applyUrl?: string
  sourceUrl?: string
  salaryMinimum?: number
  salaryMaximum?: number
  salaryCurrency?: string
  salaryInterval?: SalaryInterval
  publishedAt?: string
  expiresAt?: string
  recordVersion?: number
}

export interface JobSummary {
  total: number
  readyForEvaluation: number
  needsReview: number
  duplicates: number
  expired: number
  sourceRemoved: number
  archived: number
}

export interface JobListFilters {
  sourceId?: string
  sourceType?: JobSourceType
  status?: JobPostingStatus
  company?: string
  title?: string
  location?: string
  workplaceType?: WorkplaceType
  employmentType?: EmploymentType
  publishedFrom?: string
  publishedTo?: string
  firstSeenFrom?: string
  firstSeenTo?: string
  includeDuplicates?: boolean
  includeArchived?: boolean
  page?: number
  size?: number
  sort?: string
}
