import type { JobSourceConfiguration, JobSourceInput, SourceRegion } from '../types/jobs'

export const defaultJobSourceInput: JobSourceInput = {
  displayName: '', sourceType: 'LEVER', providerIdentifier: '', region: 'GLOBAL', enabled: true,
  pageSize: 50, maximumPagesPerRun: 10, missingRunThreshold: 2,
}

export function sourceToInput(source: JobSourceConfiguration): JobSourceInput {
  return { displayName: source.displayName, sourceType: source.sourceType, providerIdentifier: source.providerIdentifier, region: source.region, enabled: source.enabled, pageSize: source.pageSize, maximumPagesPerRun: source.maximumPagesPerRun, missingRunThreshold: source.missingRunThreshold, recordVersion: source.recordVersion }
}

export function regionsFor(sourceType: JobSourceInput['sourceType']): SourceRegion[] {
  return sourceType === 'LEVER' ? ['GLOBAL', 'EU'] : ['DEFAULT']
}

export function validateJobSource(value: JobSourceInput) {
  if (!value.displayName.trim()) return 'Display name is required'
  if (!value.providerIdentifier.trim()) return 'Provider identifier is required'
  const identifierPattern = value.sourceType === 'EMAIL_WEBHOOK' ? /^[A-Za-z0-9][A-Za-z0-9 ._-]{0,199}$/ : /^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/
  if (!identifierPattern.test(value.providerIdentifier.trim()) || value.providerIdentifier.includes('://') || value.providerIdentifier.includes('/') || value.providerIdentifier.includes('\\')) return 'Enter a provider site, board token, or logical source name—not a URL'
  if (!regionsFor(value.sourceType).includes(value.region)) return `Region ${value.region} is not valid for ${value.sourceType}`
  if (!Number.isInteger(value.pageSize) || value.pageSize < 1 || value.pageSize > 100) return 'Page size must be between 1 and 100'
  if (!Number.isInteger(value.maximumPagesPerRun) || value.maximumPagesPerRun < 1 || value.maximumPagesPerRun > 100) return 'Maximum pages per run must be between 1 and 100'
  if (!Number.isInteger(value.missingRunThreshold) || value.missingRunThreshold < 1 || value.missingRunThreshold > 20) return 'Missing-run threshold must be between 1 and 20'
  return ''
}
