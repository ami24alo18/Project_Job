export type EvaluationStatus = 'QUEUED' | 'RULE_FILTERED' | 'AI_PENDING' | 'AI_RUNNING' | 'SUCCEEDED' | 'NEEDS_REVIEW' | 'FAILED' | 'STALE' | 'CANCELLED'
export type EvaluationRecommendation = 'STRONG_APPLY' | 'APPLY' | 'MANUAL_REVIEW' | 'SKIP'

export interface JobEvaluation {
  id: string
  jobId: string
  profileVersionId: string
  ruleEvaluationId?: string
  status: EvaluationStatus
  recommendation?: EvaluationRecommendation
  overallScore?: number
  confidence?: number
  safeErrorCode?: string
  safeErrorMessage?: string
  stale: boolean
  createdAt: string
  completedAt?: string
}
