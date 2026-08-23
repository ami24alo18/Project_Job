import { apiClient } from './apiClient'
import type { JobInput, JobListFilters, JobPosting, JobSummary, PagedResponse } from '../types/jobs'

export const jobsApi = {
  list: async (params?: JobListFilters) => (await apiClient.get<PagedResponse<JobPosting>>('/api/v1/jobs', { params })).data,
  get: async (id: string) => (await apiClient.get<JobPosting>(`/api/v1/jobs/${id}`)).data,
  update: async (id: string, body: JobInput) => (await apiClient.put<JobPosting>(`/api/v1/jobs/${id}`, body)).data,
  createManual: async (body: JobInput, idempotencyKey: string) =>
    (await apiClient.post<JobPosting>('/api/v1/jobs/manual', body, { headers: { 'Idempotency-Key': idempotencyKey } })).data,
  action: async (id: string, action: 'archive' | 'restore' | 'mark-expired') =>
    (await apiClient.post<JobPosting>(`/api/v1/jobs/${id}/${action}`)).data,
  duplicates: async (id: string) => (await apiClient.get<JobPosting[]>(`/api/v1/jobs/${id}/duplicates`)).data,
  summary: async () => (await apiClient.get<JobSummary>('/api/v1/jobs/summary')).data,
}
