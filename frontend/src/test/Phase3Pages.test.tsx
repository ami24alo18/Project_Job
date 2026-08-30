import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { jobSourcesApi } from '../api/jobSourcesApi'
import { jobsApi } from '../api/jobsApi'
import { evaluationsApi } from '../api/evaluationsApi'
import { matchingApi, type MatchingConfiguration } from '../api/matchingApi'
import { jobFormToInput, jobToForm } from '../utils/jobForms'
import { regionsFor } from '../utils/jobSourceForms'
import { JobSourceDetailPage } from '../pages/JobSourceDetailPage'
import { JobSourceRunsPage, RUN_POLL_INTERVAL_MS, RUN_POLL_LIMIT } from '../pages/JobSourceRunsPage'
import { JobSourcesPage } from '../pages/JobSourcesPage'
import { JobDetailPage } from '../pages/JobDetailPage'
import { JobsPage } from '../pages/JobsPage'
import { NewJobPage } from '../pages/NewJobPage'
import { MatchingSettingsPage } from '../pages/MatchingSettingsPage'
import type { JobPosting, JobSourceConfiguration, JobSourceRun, PagedResponse } from '../types/jobs'
import type { JobEvaluation } from '../types/evaluations'

const source: JobSourceConfiguration = { id: 'source-1', displayName: 'Fictional Lever Board', sourceType: 'LEVER', providerIdentifier: 'fictional-company', region: 'GLOBAL', enabled: true, pageSize: 50, maximumPagesPerRun: 10, missingRunThreshold: 2, consecutiveFailureCount: 0, recordVersion: 1, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z' }
const run: JobSourceRun = { id: 'run-1', sourceId: source.id, triggerType: 'MANUAL', status: 'SUCCEEDED', startedAt: '2026-08-22T10:00:00Z', completedAt: '2026-08-22T10:00:02Z', discoveredCount: 4, createdCount: 2, updatedCount: 1, unchangedCount: 0, duplicateCount: 1, failedCount: 0, removedCount: 0, createdAt: '2026-08-22T10:00:00Z' }
const job: JobPosting = { id: 'job-1', sourceId: source.id, sourceType: 'LEVER', externalId: 'posting-1', company: 'Fictional Company', title: 'Backend Engineer', location: 'Noida', countryCode: 'IN', workplaceType: 'HYBRID', employmentType: 'FULL_TIME', descriptionPlainText: 'Build a fictional service.', descriptionTruncated: false, applyUrl: 'https://jobs.example.test/apply/1', sourceUrl: 'https://jobs.example.test/posting/1', publishedAt: '2026-08-20T08:00:00Z', firstSeenAt: '2026-08-22T10:00:00Z', lastSeenAt: '2026-08-22T10:00:00Z', missingSuccessfulRunCount: 0, fingerprint: 'fingerprint', contentHash: 'content-hash', status: 'READY_FOR_EVALUATION', manuallyEdited: false, recordVersion: 0, createdAt: '2026-08-22T10:00:00Z', updatedAt: '2026-08-22T10:00:00Z' }
const matchingConfiguration: MatchingConfiguration = { id: 'matching-1', profileId: 'profile-1', skillsWeight: 35, experienceWeight: 20, roleWeight: 15, locationWeight: 15, domainWeight: 10, compensationWeight: 5, strongApplyThreshold: 85, applyThreshold: 75, manualReviewThreshold: 60, maximumAllowedExperienceGap: 1, maximumJobsPerBatch: 25, maximumDailyAiRequests: 100, maximumDailyInputTokens: 500000, rulesetVersion: 'v1', recordVersion: 2, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-02T00:00:00Z' }
const emptyPage = <T,>(content: T[] = []): PagedResponse<T> => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 })

function router(element: React.ReactNode, initial = '/') {
  return render(<MemoryRouter initialEntries={[initial]}>{element}</MemoryRouter>)
}

async function choose(label: string, option: string) {
  fireEvent.mouseDown(screen.getByLabelText(label))
  fireEvent.click(await screen.findByText(option))
}

beforeEach(() => { vi.restoreAllMocks(); vi.spyOn(evaluationsApi, 'list').mockResolvedValue([]) })
afterEach(() => vi.useRealTimers())

describe('Phase 3 source pages', () => {
  it('updates matching settings without sending response-only fields', async () => {
    vi.spyOn(matchingApi, 'get').mockResolvedValue(matchingConfiguration)
    const put = vi.spyOn(matchingApi, 'put').mockResolvedValue(matchingConfiguration)
    router(<MatchingSettingsPage />)
    await screen.findByDisplayValue('500000')
    fireEvent.click(screen.getByRole('button', { name: 'Save settings' }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    const request = put.mock.calls[0][0] as Record<string, unknown>
    expect(request).toMatchObject({ skillsWeight: 35, recordVersion: 2, rulesetVersion: 'v1' })
    expect(request).not.toHaveProperty('id')
    expect(request).not.toHaveProperty('profileId')
    expect(request).not.toHaveProperty('createdAt')
    expect(request).not.toHaveProperty('updatedAt')
  })

  it('creates a provider-specific source without an arbitrary URL field', async () => {
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValue([])
    vi.spyOn(jobSourcesApi, 'runs').mockResolvedValue(emptyPage())
    const create = vi.spyOn(jobSourcesApi, 'create').mockResolvedValue({ ...source, sourceType: 'GREENHOUSE', region: 'DEFAULT' })
    router(<JobSourcesPage />)
    await screen.findByText('No job sources configured.')
    await choose('Source type', 'Greenhouse')
    fireEvent.change(screen.getByLabelText(/^Display name/i), { target: { value: 'Fictional Greenhouse Board' } })
    fireEvent.change(screen.getByLabelText(/Greenhouse board token/i), { target: { value: 'fictional-board' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add source' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith(expect.objectContaining({ sourceType: 'GREENHOUSE', region: 'DEFAULT', providerIdentifier: 'fictional-board' })))
    expect(screen.queryByLabelText(/base url/i)).not.toBeInTheDocument()
  })

  it('enforces source-type-specific regions', () => {
    expect(regionsFor('LEVER')).toEqual(['GLOBAL', 'EU'])
    expect(regionsFor('GREENHOUSE')).toEqual(['DEFAULT'])
    expect(regionsFor('EMAIL_WEBHOOK')).toEqual(['DEFAULT'])
  })

  it('supports disable, enable, and manual synchronization actions', async () => {
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValueOnce([source]).mockResolvedValueOnce([{ ...source, enabled: false }]).mockResolvedValue([source])
    vi.spyOn(jobSourcesApi, 'sourceRuns').mockResolvedValue(emptyPage([run]))
    const action = vi.spyOn(jobSourcesApi, 'action').mockImplementation(async (_id, name) => ({ ...source, enabled: name === 'enable' }))
    const sync = vi.spyOn(jobSourcesApi, 'sync').mockResolvedValue({ runId: 'queued-run', status: 'QUEUED' })
    router(<JobSourcesPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Disable' }))
    await waitFor(() => expect(action).toHaveBeenCalledWith(source.id, 'disable'))
    fireEvent.click(await screen.findByRole('button', { name: 'Enable' }))
    await waitFor(() => expect(action).toHaveBeenCalledWith(source.id, 'enable'))
    fireEvent.click(await screen.findByRole('button', { name: 'Run sync' }))
    await waitFor(() => expect(sync).toHaveBeenCalledWith(source.id))
    expect(await screen.findByText(/queued-run/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Archive' }))
    await waitFor(() => expect(action).toHaveBeenCalledWith(source.id, 'archive'))
  })

  it('loads each source latest run directly and reports partial run-metric failures', async () => {
    const secondSource = { ...source, id: 'source-2', displayName: 'Fictional Greenhouse Board', sourceType: 'GREENHOUSE' as const, region: 'DEFAULT' as const }
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValue([source, secondSource])
    const globalRuns = vi.spyOn(jobSourcesApi, 'runs').mockResolvedValue(emptyPage())
    const sourceRuns = vi.spyOn(jobSourcesApi, 'sourceRuns').mockImplementation(async id => {
      if (id === source.id) return emptyPage([run])
      throw new Error('temporary run history failure')
    })
    router(<JobSourcesPage />)
    expect(await screen.findByText(/Created 2 · Updated 1 · Unchanged 0 · Duplicates 1/)).toBeInTheDocument()
    expect(screen.getByText(/Could not load the latest synchronization status for Fictional Greenhouse Board/)).toBeInTheDocument()
    expect(sourceRuns).toHaveBeenCalledWith(source.id, { page: 0, size: 1 })
    expect(sourceRuns).toHaveBeenCalledWith(secondSource.id, { page: 0, size: 1 })
    expect(globalRuns).not.toHaveBeenCalled()
  })

  it('explains an optimistic-lock conflict while editing a source', async () => {
    vi.spyOn(jobSourcesApi, 'get').mockResolvedValue(source)
    vi.spyOn(jobSourcesApi, 'sourceRuns').mockResolvedValue(emptyPage())
    vi.spyOn(jobSourcesApi, 'update').mockRejectedValue({ isAxiosError: true, response: { status: 409 } })
    router(<Routes><Route path="/job-sources/:sourceId" element={<JobSourceDetailPage />} /></Routes>, `/job-sources/${source.id}`)
    fireEvent.click(await screen.findByRole('button', { name: 'Save source' }))
    expect(await screen.findByText(/changed elsewhere/i)).toBeInTheDocument()
  })

  it('keeps a source visible when its run history cannot be loaded', async () => {
    vi.spyOn(jobSourcesApi, 'get').mockResolvedValue(source)
    vi.spyOn(jobSourcesApi, 'sourceRuns').mockRejectedValue(new Error('temporary run history failure'))
    router(<Routes><Route path="/job-sources/:sourceId" element={<JobSourceDetailPage />} /></Routes>, `/job-sources/${source.id}`)
    expect(await screen.findByRole('heading', { name: source.displayName })).toBeInTheDocument()
    expect(screen.getByText('Could not load synchronization history')).toBeInTheDocument()
    expect(screen.queryByText('This source has no synchronization runs.')).not.toBeInTheDocument()
  })

  it('stops automatic run polling at the configured limit', async () => {
    vi.useFakeTimers()
    const activeRun = { ...run, status: 'RUNNING' as const, completedAt: undefined }
    vi.spyOn(jobSourcesApi, 'list').mockResolvedValue([source])
    const runs = vi.spyOn(jobSourcesApi, 'runs').mockResolvedValue(emptyPage([activeRun]))
    router(<JobSourceRunsPage />)
    await act(async () => { await Promise.resolve(); await Promise.resolve() })
    expect(screen.getByText('RUNNING')).toBeInTheDocument()
    for (let attempt = 0; attempt < RUN_POLL_LIMIT; attempt += 1) {
      await act(async () => { await vi.advanceTimersByTimeAsync(RUN_POLL_INTERVAL_MS) })
    }
    expect(runs).toHaveBeenCalledTimes(RUN_POLL_LIMIT + 1)
    expect(screen.getByText(new RegExp(`stopped after ${RUN_POLL_LIMIT} attempts`, 'i'))).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(RUN_POLL_INTERVAL_MS * 2) })
    expect(runs).toHaveBeenCalledTimes(RUN_POLL_LIMIT + 1)
  })

  it('reports when sources cannot be loaded for the run filter', async () => {
    vi.spyOn(jobSourcesApi, 'list').mockRejectedValue(new Error('temporary source-list failure'))
    vi.spyOn(jobSourcesApi, 'runs').mockResolvedValue(emptyPage())
    router(<JobSourceRunsPage />)
    expect(await screen.findByText('Could not load sources for filtering')).toBeInTheDocument()
  })
})

describe('Phase 3 job pages', () => {
  it('round-trips stored instants through local datetime fields without changing them', () => {
    const stored = { ...job, publishedAt: '2026-08-20T08:12:34.567Z', expiresAt: '2026-09-30T17:45:06.123Z' }
    const form = jobToForm(stored)
    const published = new Date(stored.publishedAt)
    const pad = (value: number, length = 2) => String(value).padStart(length, '0')
    expect(form.publishedAt).toBe(`${published.getFullYear()}-${pad(published.getMonth() + 1)}-${pad(published.getDate())}T${pad(published.getHours())}:${pad(published.getMinutes())}:${pad(published.getSeconds())}.${pad(published.getMilliseconds(), 3)}`)
    const input = jobFormToInput(form, stored.recordVersion)
    expect(input.publishedAt).toBe(stored.publishedAt)
    expect(input.expiresAt).toBe(stored.expiresAt)
  })

  it('applies filters, excludes duplicates and archived jobs by default, and paginates', async () => {
    const page = { content: [job], page: 0, size: 20, totalElements: 21, totalPages: 2 }
    const list = vi.spyOn(jobsApi, 'list').mockResolvedValue(page)
    router(<JobsPage />)
    expect(await screen.findByText(job.title)).toBeInTheDocument()
    expect(list).toHaveBeenCalledWith(expect.objectContaining({ includeDuplicates: false, includeArchived: false, page: 0, sort: 'firstSeenAt,desc' }))
    fireEvent.change(screen.getByLabelText('Title search'), { target: { value: 'platform' } })
    fireEvent.click(screen.getByLabelText('Include duplicates'))
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }))
    await waitFor(() => expect(list).toHaveBeenCalledWith(expect.objectContaining({ title: 'platform', includeDuplicates: true })))
    fireEvent.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(list).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })))
  })

  it('renders a useful empty state', async () => {
    vi.spyOn(jobsApi, 'list').mockResolvedValue(emptyPage())
    router(<JobsPage />)
    expect(await screen.findByText('No jobs match these filters.')).toBeInTheDocument()
  })

  it('validates required manual-job content before calling the API', async () => {
    const create = vi.spyOn(jobsApi, 'createManual')
    router(<NewJobPage />)
    fireEvent.click(screen.getByRole('button', { name: 'Create manual job' }))
    expect(await screen.findByText('Company and title are required')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText(/Company/i), { target: { value: 'Fictional Company' } })
    fireEvent.change(screen.getByLabelText(/Job title/i), { target: { value: 'Engineer' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create manual job' }))
    expect(await screen.findByText('Enter a description or an application URL')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('reuses the idempotency key when a manual submission is retried', async () => {
    const create = vi.spyOn(jobsApi, 'createManual').mockRejectedValueOnce(new Error('temporary')).mockResolvedValue({ ...job, id: 'manual-job', sourceId: undefined, sourceType: 'MANUAL' })
    router(<Routes><Route path="/jobs/new" element={<NewJobPage />} /><Route path="/jobs/:jobId" element={<div>Created job route</div>} /></Routes>, '/jobs/new')
    fireEvent.change(screen.getByLabelText(/Company/i), { target: { value: 'Fictional Company' } })
    fireEvent.change(screen.getByLabelText(/Job title/i), { target: { value: 'Manual Engineer' } })
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'A fictional manual job.' } })
    const submit = screen.getByRole('button', { name: 'Create manual job' })
    fireEvent.click(submit)
    expect(await screen.findByText('Manual job could not be created')).toBeInTheDocument()
    const retry = screen.getByRole('button', { name: 'Create manual job' })
    await waitFor(() => expect(retry).toBeEnabled())
    fireEvent.click(retry)
    await waitFor(() => expect(create).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('Created job route')).toBeInTheDocument()
    expect(create.mock.calls[0][1]).toBe(create.mock.calls[1][1])
    expect(create.mock.calls[1][0]).toEqual(expect.objectContaining({ company: 'Fictional Company', title: 'Manual Engineer', description: 'A fictional manual job.' }))
  })

  it('renders descriptions as text, uses safe link attributes, and shows duplicate relationships', async () => {
    const unsafe = { ...job, descriptionPlainText: '<img src=x onerror=alert(1)><script>alert(2)</script>', sourceUrl: 'javascript:alert(1)', status: 'DUPLICATE' as const, duplicateOfJobId: 'original-job' }
    vi.spyOn(jobsApi, 'get').mockResolvedValue(unsafe)
    vi.spyOn(jobsApi, 'duplicates').mockResolvedValue([])
    router(<Routes><Route path="/jobs/:jobId" element={<JobDetailPage />} /></Routes>, `/jobs/${job.id}`)
    expect(await screen.findByText(unsafe.descriptionPlainText)).toBeInTheDocument()
    expect(document.querySelector('img')).toBeNull()
    expect(document.querySelector('script')).toBeNull()
    const apply = screen.getByRole('link', { name: 'Open official application' })
    expect(apply).toHaveAttribute('target', '_blank')
    expect(apply).toHaveAttribute('rel', 'noopener noreferrer')
    expect(screen.queryByRole('link', { name: 'Open source posting' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /job original-job/i })).toHaveAttribute('href', '/jobs/original-job')
  })

  it('keeps job details visible when related duplicates cannot be loaded', async () => {
    vi.spyOn(jobsApi, 'get').mockResolvedValue(job)
    vi.spyOn(jobsApi, 'duplicates').mockRejectedValue(new Error('temporary duplicate lookup failure'))
    router(<Routes><Route path="/jobs/:jobId" element={<JobDetailPage />} /></Routes>, `/jobs/${job.id}`)
    expect(await screen.findByRole('heading', { name: job.title })).toBeInTheDocument()
    expect(screen.getByText('Could not load duplicate relationships')).toBeInTheDocument()
  })

  it('evaluates a job from its detail page and displays the result', async () => {
    const evaluation: JobEvaluation = { id: 'evaluation-1', jobId: job.id, profileVersionId: 'profile-version-1', ruleEvaluationId: 'rule-1', status: 'NEEDS_REVIEW', recommendation: 'MANUAL_REVIEW', overallScore: 72, confidence: 80, safeErrorCode: 'AI_DISABLED', safeErrorMessage: 'AI evaluation is unavailable; deterministic result remains available', stale: false, createdAt: '2026-08-30T12:00:00Z', completedAt: '2026-08-30T12:00:01Z' }
    vi.spyOn(jobsApi, 'get').mockResolvedValue(job)
    vi.spyOn(jobsApi, 'duplicates').mockResolvedValue([])
    const evaluate = vi.spyOn(evaluationsApi, 'evaluate').mockResolvedValue(evaluation)
    router(<Routes><Route path="/jobs/:jobId" element={<JobDetailPage />} /></Routes>, `/jobs/${job.id}`)
    fireEvent.click(await screen.findByRole('button', { name: 'Evaluate job' }))
    await waitFor(() => expect(evaluate).toHaveBeenCalledWith(job.id))
    expect(await screen.findByText('NEEDS_REVIEW')).toBeInTheDocument()
    expect(screen.getByText(/AI scoring is disabled/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate application draft' })).toBeEnabled()
  })

  it('archives an active job', async () => {
    vi.spyOn(jobsApi, 'get').mockResolvedValue(job)
    vi.spyOn(jobsApi, 'duplicates').mockResolvedValue([])
    const action = vi.spyOn(jobsApi, 'action').mockResolvedValue({ ...job, status: 'ARCHIVED' })
    router(<Routes><Route path="/jobs/:jobId" element={<JobDetailPage />} /></Routes>, `/jobs/${job.id}`)
    fireEvent.click(await screen.findByRole('button', { name: 'Archive' }))
    await waitFor(() => expect(action).toHaveBeenCalledWith(job.id, 'archive'))
    expect(await screen.findByText(/status changed to ARCHIVED/i)).toBeInTheDocument()
  })

  it('restores an archived job', async () => {
    vi.spyOn(jobsApi, 'get').mockResolvedValue({ ...job, status: 'ARCHIVED' })
    vi.spyOn(jobsApi, 'duplicates').mockResolvedValue([])
    const action = vi.spyOn(jobsApi, 'action').mockResolvedValue(job)
    router(<Routes><Route path="/jobs/:jobId" element={<JobDetailPage />} /></Routes>, `/jobs/${job.id}`)
    fireEvent.click(await screen.findByRole('button', { name: 'Restore' }))
    await waitFor(() => expect(action).toHaveBeenCalledWith(job.id, 'restore'))
    expect(await screen.findByText(/status changed to READY_FOR_EVALUATION/i)).toBeInTheDocument()
  })

  it('explains a stale-version conflict when editing a job', async () => {
    vi.spyOn(jobsApi, 'get').mockResolvedValue(job)
    vi.spyOn(jobsApi, 'duplicates').mockResolvedValue([])
    vi.spyOn(jobsApi, 'update').mockRejectedValue({ isAxiosError: true, response: { status: 409 } })
    router(<Routes><Route path="/jobs/:jobId" element={<JobDetailPage />} /></Routes>, `/jobs/${job.id}`)
    fireEvent.click(await screen.findByRole('button', { name: 'Edit job' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText(/changed elsewhere/i)).toBeInTheDocument()
  })
})
