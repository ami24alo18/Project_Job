import { apiClient } from './apiClient'
import type { CareerSiteDiscovery, CareerSiteDiscoveryInput, CareerSiteJobSourceInput, ExternalJobSourceCreated, ExternalJobSourceInput, JobSourceConfiguration, JobSourceConnectionTest, JobSourceInput, JobSourceRun, JobSourceRunStatus, JobSourceSearchRule, JobSourceSearchRuleInput, JobSpyPolicy, PagedResponse, SyncAccepted, WebhookTokenRotation } from '../types/jobs'

export const jobSourcesApi = {
  create: async (body: JobSourceInput) => (await apiClient.post<JobSourceConfiguration>('/api/v1/job-sources', body)).data,
  createExternal: async (body: ExternalJobSourceInput) => (await apiClient.post<ExternalJobSourceCreated>('/api/v1/job-sources/external', body)).data,
  jobSpyPolicy: async () => (await apiClient.get<JobSpyPolicy>('/api/v1/job-sources/jobspy-policy')).data,
  discoverCareerSite: async (body: CareerSiteDiscoveryInput) => (await apiClient.post<CareerSiteDiscovery>('/api/v1/job-sources/discover', body)).data,
  createCareerSite: async (body: CareerSiteJobSourceInput) => (await apiClient.post<JobSourceConfiguration>('/api/v1/job-sources/career-site', body)).data,
  testConnection: async (sourceId: string) => (await apiClient.post<JobSourceConnectionTest>(`/api/v1/job-sources/${sourceId}/test`)).data,
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
  rotateToken: async (sourceId: string) => (await apiClient.post<WebhookTokenRotation>(`/api/v1/job-sources/${sourceId}/rotate-token`)).data,
  searchRules: async (sourceId: string) => (await apiClient.get<JobSourceSearchRule[]>(`/api/v1/job-sources/${sourceId}/search-rules`)).data,
  createSearchRule: async (sourceId: string, body: JobSourceSearchRuleInput) => (await apiClient.post<JobSourceSearchRule>(`/api/v1/job-sources/${sourceId}/search-rules`, body)).data,
  updateSearchRule: async (sourceId: string, ruleId: string, body: JobSourceSearchRuleInput) => (await apiClient.put<JobSourceSearchRule>(`/api/v1/job-sources/${sourceId}/search-rules/${ruleId}`, body)).data,
  setSearchRuleEnabled: async (sourceId: string, ruleId: string, enabled: boolean) => (await apiClient.post<JobSourceSearchRule>(`/api/v1/job-sources/${sourceId}/search-rules/${ruleId}/${enabled ? 'enable' : 'disable'}`)).data,
}
