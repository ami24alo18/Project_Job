import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { jobSourcesApi } from '../api/jobSourcesApi'
import { JobSourceDetailPage } from '../pages/JobSourceDetailPage'
import { JobSourcesPage } from '../pages/JobSourcesPage'
import type { CareerSiteDiscovery, JobSourceConfiguration, PagedResponse } from '../types/jobs'

const discovery: CareerSiteDiscovery = {
  canonicalUrl: 'https://jobs.smartrecruiters.com/ExampleCompany',
  canonicalHost: 'jobs.smartrecruiters.com',
  connectorType: 'SMARTRECRUITERS',
  providerIdentifier: 'ExampleCompany',
  supportStatus: 'SUPPORTED',
  supportMessage: 'The public SmartRecruiters Posting API is supported',
  detectionVersion: 'career-site-detection-v1',
}

const careerSource: JobSourceConfiguration = {
  id: 'career-source-1', displayName: 'Example Company', sourceType: 'CAREER_SITE',
  sourceCategory: 'PULL_FEED', connectorType: 'SMARTRECRUITERS', providerIdentifier: 'ExampleCompany',
  region: 'DEFAULT', careerSiteUrl: discovery.canonicalUrl, canonicalHost: discovery.canonicalHost,
  supportStatus: 'SUPPORTED', supportMessage: discovery.supportMessage, enabled: true,
  pageSize: 50, maximumPagesPerRun: 20, missingRunThreshold: 2, consecutiveFailureCount: 0,
  recordVersion: 0, createdAt: '2026-08-29T00:00:00Z', updatedAt: '2026-08-29T00:00:00Z',
}

const page = <T,>(content: T[] = []): PagedResponse<T> => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })

beforeEach(() => vi.restoreAllMocks())

describe('career-site onboarding', () => {
  it('inspects first and creates without trusting connector fields from the browser', async () => {
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValue([])
    const inspect = vi.spyOn(jobSourcesApi, 'discoverCareerSite').mockResolvedValue(discovery)
    const create = vi.spyOn(jobSourcesApi, 'createCareerSite').mockResolvedValue(careerSource)
    render(<MemoryRouter><JobSourcesPage /></MemoryRouter>)
    await screen.findByText('No job sources configured.')

    fireEvent.change(screen.getByLabelText(/Company name/), { target: { value: 'Example Company' } })
    fireEvent.change(screen.getByLabelText(/Career-site URL/), { target: { value: discovery.canonicalUrl } })
    fireEvent.click(screen.getByRole('button', { name: 'Inspect career site' }))

    await waitFor(() => expect(inspect).toHaveBeenCalledWith({ companyName: 'Example Company', careerSiteUrl: discovery.canonicalUrl }))
    expect(await screen.findByText('SMARTRECRUITERS')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Create career source' }))

    await waitFor(() => expect(create).toHaveBeenCalledWith({
      companyName: 'Example Company', careerSiteUrl: discovery.canonicalUrl, enabled: true,
      pageSize: 50, maximumPagesPerRun: 20, missingRunThreshold: 2,
    }))
    expect(create.mock.calls[0][0]).not.toHaveProperty('connectorType')
  })

  it('shows safe follow-up guidance and saves unsupported detection disabled', async () => {
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValue([])
    vi.spyOn(jobSourcesApi, 'discoverCareerSite').mockResolvedValue({ ...discovery,
      connectorType: 'ORACLE_CX', supportStatus: 'NEEDS_AUTHORIZATION',
      supportMessage: 'The detected Oracle integration requires an approved public interface before activation',
    })
    const create = vi.spyOn(jobSourcesApi, 'createCareerSite').mockResolvedValue({ ...careerSource,
      connectorType: 'ORACLE_CX', supportStatus: 'NEEDS_AUTHORIZATION', enabled: false,
    })
    render(<MemoryRouter><JobSourcesPage /></MemoryRouter>)
    await screen.findByText('No job sources configured.')
    fireEvent.change(screen.getByLabelText(/Company name/), { target: { value: 'Example Company' } })
    fireEvent.change(screen.getByLabelText(/Career-site URL/), { target: { value: discovery.canonicalUrl } })
    fireEvent.click(screen.getByRole('button', { name: 'Inspect career site' }))

    expect(await screen.findByText(/requires an approved public interface/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Save disabled source' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith(expect.objectContaining({ enabled: false })))
  })

  it('connection-tests a supported career adapter without persisting a run', async () => {
    vi.spyOn(jobSourcesApi, 'get').mockResolvedValueOnce(careerSource).mockResolvedValueOnce({ ...careerSource,
      lastConnectionTestStatus: 'SUCCEEDED', lastConnectionTestAt: '2026-08-29T10:00:00Z',
    })
    vi.spyOn(jobSourcesApi, 'sourceRuns').mockResolvedValue(page())
    const test = vi.spyOn(jobSourcesApi, 'testConnection').mockResolvedValue({
      source: careerSource, status: 'SUCCEEDED', discoveredCount: 3,
      message: 'The reviewed adapter returned a valid bounded response',
    })
    render(<MemoryRouter initialEntries={[`/job-sources/${careerSource.id}`]}><Routes>
      <Route path="/job-sources/:sourceId" element={<JobSourceDetailPage />} />
    </Routes></MemoryRouter>)

    await screen.findByRole('heading', { name: careerSource.displayName })
    fireEvent.click(screen.getByRole('button', { name: 'Test connection' }))
    await waitFor(() => expect(test).toHaveBeenCalledWith(careerSource.id))
    expect(await screen.findByText(/3 records inspected/)).toBeInTheDocument()
    expect(screen.getByText(/Last connection test: SUCCEEDED/)).toBeInTheDocument()
  })
})
