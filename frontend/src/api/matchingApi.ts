import { apiClient } from './apiClient'

export type MatchingConfiguration = {
  id: string
  profileId: string
  skillsWeight: number
  experienceWeight: number
  roleWeight: number
  locationWeight: number
  domainWeight: number
  compensationWeight: number
  strongApplyThreshold: number
  applyThreshold: number
  manualReviewThreshold: number
  maximumAllowedExperienceGap: number
  maximumJobsPerBatch: number
  maximumDailyAiRequests: number
  maximumDailyInputTokens: number
  rulesetVersion: string
  recordVersion: number
  createdAt: string
  updatedAt: string
}

export type MatchingConfigurationUpdate = Omit<MatchingConfiguration, 'id' | 'profileId' | 'createdAt' | 'updatedAt'>

export const matchingApi = {
  get: async () => (await apiClient.get<MatchingConfiguration>('/api/v1/matching/configuration')).data,
  put: async (value: MatchingConfigurationUpdate) =>
    (await apiClient.put<MatchingConfiguration>('/api/v1/matching/configuration', value)).data,
}
