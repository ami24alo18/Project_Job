import { apiClient } from './apiClient'
import type { JobSourceConfiguration, JobSourceInput, JobSourceRun, JobSourceRunStatus, PagedResponse, SyncAccepted } from '../types/jobs'

export const jobSourcesApi = {
  create: async (body: JobSourceInput) => (await apiClient.post<JobSourceConfiguration>('/api/v1/job-sources', body)).data,
  list: async () => (await apiClient.get<JobSourceConfiguration[]>('/api/v1/job-sources')).data,
  get: async (id: string) => (await apiClient.get<JobSourceConfiguration>(`/api/v1/job-sources/${id}`)).data,
  update: async (id: string, body: JobSourceInput) => (await apiClient.put<JobSourceConfiguration>(`/api/v1/job-sources/${id}`, body)).data,
  action: async (id: string, action: 'enable' | 'disable' | 'archive') => (await apiClient.post<JobSourceConfiguration>(`/api/v1/job-sources/${id}/${action}`)).data,
  sync: async (id: string) => (await apiClient.post<SyncAccepted>(`/api/v1/job-sources/${id}/sync`)).data,
  syncEnabled: async () => (await apiClient.post<{ runs: SyncAccepted[] }>('/api/v1/job-sources/sync-enabled')).data,
  runs: async (params?: { sourceId?: string; status?: JobSourceRunStatus; page?: number; size?: number }) =>
    (await apiClient.get<PagedResponse<JobSourceRun>>('/api/v1/job-source-runs', { params })).data,
  run: async (id: string) => (await apiClient.get<JobSourceRun>(`/api/v1/job-source-runs/${id}`)).data,
  sourceRuns: async (sourceId: string, params?: { page?: number; size?: number }) =>
    (await apiClient.get<PagedResponse<JobSourceRun>>(`/api/v1/job-sources/${sourceId}/runs`, { params })).data,
}
