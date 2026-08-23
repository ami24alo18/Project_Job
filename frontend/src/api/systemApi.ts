import { apiClient } from './apiClient'
import type { SystemHealth } from '../types/system'

export async function getSystemHealth(): Promise<SystemHealth> {
  const { data } = await apiClient.get<unknown>('/api/v1/system/health')
  if (!isSystemHealth(data)) throw new Error('Unexpected health response')
  return data
}

function isSystemHealth(value: unknown): value is SystemHealth {
  if (!value || typeof value !== 'object') return false
  const item = value as Record<string, unknown>
  return item.status === 'UP' && typeof item.service === 'string' && typeof item.timestamp === 'string'
}
