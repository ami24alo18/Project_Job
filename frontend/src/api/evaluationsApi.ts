import { apiClient } from './apiClient'
import type { JobEvaluation } from '../types/evaluations'

export const evaluationsApi = {
  evaluate: async (jobId: string, force = false) =>
    (await apiClient.post<JobEvaluation>(`/api/v1/jobs/${jobId}/evaluate`, undefined, { params: { force } })).data,
  list: async (jobId: string) =>
    (await apiClient.get<JobEvaluation[]>(`/api/v1/jobs/${jobId}/evaluations`)).data,
  get: async (evaluationId: string) =>
    (await apiClient.get<JobEvaluation>(`/api/v1/job-evaluations/${evaluationId}`)).data,
}
