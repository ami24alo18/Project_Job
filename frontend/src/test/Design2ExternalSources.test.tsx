import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { jobSourcesApi } from '../api/jobSourcesApi'
import { JobSourceDetailPage } from '../pages/JobSourceDetailPage'
import { JobSourceRunsPage } from '../pages/JobSourceRunsPage'
import { JobSourcesPage } from '../pages/JobSourcesPage'
import type { JobSourceConfiguration, JobSourceRun, JobSourceSearchRule, PagedResponse } from '../types/jobs'

const externalSource: JobSourceConfiguration = {
  id: 'source-external',
  displayName: 'LinkedIn through JSearch',
  sourceType: 'EXTERNAL_API',
  sourceCategory: 'PUSH_WEBHOOK',
  connectorType: 'JSEARCH',
  providerIdentifier: 'linkedin-jsearch',
  region: 'DEFAULT',
  supportStatus: 'SUPPORTED',
  webhookConfigured: true,
  enabled: true,
  pageSize: 100,
  maximumPagesPerRun: 1,
  missingRunThreshold: 2,
  consecutiveFailureCount: 0,
  recordVersion: 0,
  createdAt: '2026-08-25T00:00:00Z',
  updatedAt: '2026-08-25T00:00:00Z',
}

const searchRule: JobSourceSearchRule = {
  id: 'rule-1',
  sourceId: externalSource.id,
  name: 'Backend roles in India',
  query: 'Senior backend engineer',
  locations: ['India'],
  remoteAllowed: true,
  hybridAllowed: true,
  onsiteAllowed: false,
  datePostedWindow: 'TODAY',
  maximumResults: 100,
  enabled: true,
  recordVersion: 0,
  createdAt: '2026-08-25T00:00:00Z',
  updatedAt: '2026-08-25T00:00:00Z',
}

const pushedRun: JobSourceRun = {
  id: 'run-pushed',
  sourceId: externalSource.id,
  triggerType: 'WEBHOOK',
  coverage: 'PUSH_BATCH',
  externalEventId: 'event-1',
  status: 'PARTIAL_SUCCESS',
  startedAt: '2026-08-25T01:00:00Z',
  completedAt: '2026-08-25T01:00:01Z',
  discoveredCount: 2,
  createdCount: 1,
  updatedCount: 0,
  unchangedCount: 0,
  duplicateCount: 0,
  failedCount: 1,
  removedCount: 0,
  createdAt: '2026-08-25T01:00:00Z',
}

const page = <T,>(content: T[] = []): PagedResponse<T> => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })

function route(element: React.ReactNode, initial = '/') {
  return render(<MemoryRouter initialEntries={[initial]}>{element}</MemoryRouter>)
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(jobSourcesApi, 'jobSpyPolicy').mockResolvedValue({ configured: true, allowedSites: ['indeed'] })
})

describe('external automation sources', () => {
  it('creates an external source and displays its token as a one-time secret', async () => {
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValue([])
    const create = vi.spyOn(jobSourcesApi, 'createExternal').mockResolvedValue({
      source: externalSource,
      webhookUrl: `/api/v1/job-sources/${externalSource.id}/external-events`,
      webhookToken: 'one-time-secret-token',
    })

    route(<JobSourcesPage />)
    await screen.findByText('No job sources configured.')
    fireEvent.change(screen.getByLabelText(/External source display name/i), { target: { value: 'LinkedIn through JSearch' } })
    fireEvent.change(screen.getByLabelText(/External source identifier/i), { target: { value: 'linkedin-jsearch' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create external source' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      displayName: 'LinkedIn through JSearch',
      providerIdentifier: 'linkedin-jsearch',
      connectorType: 'JSEARCH',
      enabled: true,
    }))
    expect(await screen.findByDisplayValue('one-time-secret-token')).toBeInTheDocument()
    expect(screen.getByText(/shown only once/i)).toBeInTheDocument()
  })

  it('creates JobSpy as a separate n8n-orchestrated external provider', async () => {
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValue([])
    const create = vi.spyOn(jobSourcesApi, 'createExternal').mockResolvedValue({
      source: { ...externalSource, connectorType: 'JOBSPY', displayName: 'JobSpy India', providerIdentifier: 'jobspy-india' },
      webhookUrl: `/api/v1/job-sources/${externalSource.id}/external-events`,
      webhookToken: 'jobspy-one-time-token',
    })
    route(<JobSourcesPage />)
    await screen.findByText('No job sources configured.')
    fireEvent.mouseDown(screen.getByLabelText('Connector'))
    fireEvent.click(await screen.findByRole('option', { name: 'JobSpy (n8n worker)' }))
    expect(screen.getByText(/Deployment-approved boards:/)).toHaveTextContent('indeed')
    fireEvent.change(screen.getByLabelText(/External source display name/i), { target: { value: 'JobSpy India' } })
    fireEvent.change(screen.getByLabelText(/External source identifier/i), { target: { value: 'jobspy-india' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create external source' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith({
      displayName: 'JobSpy India', providerIdentifier: 'jobspy-india', connectorType: 'JOBSPY', enabled: true,
    }))
  })

  it('shows webhook and search-rule controls without offering pull synchronization', async () => {
    vi.spyOn(jobSourcesApi, 'get').mockResolvedValue(externalSource)
    vi.spyOn(jobSourcesApi, 'sourceRuns').mockResolvedValue(page())
    vi.spyOn(jobSourcesApi, 'searchRules').mockResolvedValue([searchRule])

    route(<Routes><Route path="/job-sources/:sourceId" element={<JobSourceDetailPage />} /></Routes>, `/job-sources/${externalSource.id}`)

    expect(await screen.findByRole('heading', { name: externalSource.displayName })).toBeInTheDocument()
    expect(screen.getByText('n8n webhook')).toBeInTheDocument()
    expect(await screen.findByText(searchRule.name)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Run sync' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save source' })).not.toBeInTheDocument()
  })

  it('creates a bounded search rule for the external workflow', async () => {
    vi.spyOn(jobSourcesApi, 'get').mockResolvedValue(externalSource)
    vi.spyOn(jobSourcesApi, 'sourceRuns').mockResolvedValue(page())
    vi.spyOn(jobSourcesApi, 'searchRules').mockResolvedValueOnce([]).mockResolvedValueOnce([searchRule])
    const create = vi.spyOn(jobSourcesApi, 'createSearchRule').mockResolvedValue(searchRule)

    route(<Routes><Route path="/job-sources/:sourceId" element={<JobSourceDetailPage />} /></Routes>, `/job-sources/${externalSource.id}`)
    await screen.findByText('No search rules configured.')
    fireEvent.change(screen.getByLabelText(/Rule name/i), { target: { value: searchRule.name } })
    fireEvent.change(screen.getByLabelText(/Search query/i), { target: { value: searchRule.query } })
    fireEvent.change(screen.getByLabelText(/Locations/), { target: { value: 'India' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add search rule' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith(externalSource.id, expect.objectContaining({
      name: searchRule.name,
      query: searchRule.query,
      locations: ['India'],
      maximumResults: 100,
    })))
    expect(await screen.findByText(searchRule.name)).toBeInTheDocument()
  })

  it('labels webhook ingestion as push-batch coverage in the unified run history', async () => {
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValue([externalSource])
    vi.spyOn(jobSourcesApi, 'runs').mockResolvedValue(page([pushedRun]))

    route(<JobSourceRunsPage />)

    expect(await screen.findByText('PUSH_BATCH')).toBeInTheDocument()
    expect(screen.getByText('WEBHOOK')).toBeInTheDocument()
    expect(screen.getByText(/Created 1 .* Failed 1/)).toBeInTheDocument()
  })
})
