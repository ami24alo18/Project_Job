export type JobSourceType = 'LEVER' | 'GREENHOUSE' | 'EMAIL_WEBHOOK' | 'MANUAL'
export type SourceRegion = 'DEFAULT' | 'GLOBAL' | 'EU'
export type JobSourceTriggerType = 'MANUAL' | 'N8N' | 'RETRY'
export type JobSourceRunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL_SUCCESS' | 'FAILED'
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
  providerIdentifier: string
  region: SourceRegion
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
  sourceType: Exclude<JobSourceType, 'MANUAL'>
  providerIdentifier: string
  region: SourceRegion
  enabled: boolean
  pageSize: number
  maximumPagesPerRun: number
  missingRunThreshold: number
  recordVersion?: number
}

export interface JobSourceRun {
  id: string
  sourceId: string
  triggerType: JobSourceTriggerType
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
