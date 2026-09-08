import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../api/apiClient'
import { applicationPackagesApi } from '../api/applicationPackagesApi'
import { evaluationsApi } from '../api/evaluationsApi'
import { jobsApi } from '../api/jobsApi'
import { ApplicationPackageDetailPage } from '../pages/ApplicationPackageDetailPage'
import { ApplicationPackageRevisionPage } from '../pages/ApplicationPackageRevisionPage'
import { ApplicationPackagesPage } from '../pages/ApplicationPackagesPage'
import { JobDetailPage } from '../pages/JobDetailPage'
import type { ApplicationPackageDetail, ApplicationPackageRevision, ApplicationPackageSummary } from '../types/applicationPackages'
import type { JobPosting } from '../types/jobs'

const content = {
  id: 'content-1', packageRevisionId: 'revision-1', contentType: 'PROFESSIONAL_SUMMARY' as const, contentKey: 'summary', sectionOrder: 1,
  text: 'Backend engineer who built a fictional Java service.', origin: 'AI_GENERATED' as const, verificationStatus: 'VERIFIED' as const,
  userEdited: false, createdAt: '2026-08-22T10:00:00Z', updatedAt: '2026-08-22T10:00:00Z', recordVersion: 0,
}
const revision: ApplicationPackageRevision = {
  id: 'revision-1', packageId: 'package-1', revisionNumber: 1, profileVersionId: 'profile-version-7', evaluationId: 'evaluation-4', status: 'READY',
  sourceJobChecksum: 'job-hash', sourceProfileChecksum: 'profile-hash', sourceEvaluationChecksum: 'evaluation-hash', generationPromptVersion: 'content-v1',
  generationSchemaVersion: 'schema-v1', templateVersion: 'ats-v1', model: 'mock-model', createdAt: '2026-08-22T10:00:00Z', completedAt: '2026-08-22T10:00:02Z',
  contents: [content],
  claims: [{ id: 'claim-1', generatedContentId: content.id, claimText: 'Built a fictional Java service', claimType: 'CANDIDATE_FACT', contentPath: '$.summary[0]', validationStatus: 'VALID', validationCodes: [], sources: [{ candidateFactId: 'fact-verified-1', jobRequirementId: 'requirement-java' }], createdAt: '2026-08-22T10:00:01Z' }],
  questions: [
    { id: 'question-1', packageRevisionId: 'revision-1', question: 'Where are you currently located?', questionHash: 'h1', normalizedQuestionKey: 'location', classification: 'VERIFIED_AUTOMATIC', draftAnswer: 'Noida', answerStatus: 'DRAFTED', confidence: 1, createdAt: '2026-08-22', updatedAt: '2026-08-22' },
    { id: 'question-2', packageRevisionId: 'revision-1', question: 'Why are you interested in this role?', questionHash: 'h2', normalizedQuestionKey: 'motivation', classification: 'SUGGESTED_REQUIRES_REVIEW', draftAnswer: 'The role aligns with verified backend experience.', answerStatus: 'DRAFTED', confidence: .6, createdAt: '2026-08-22', updatedAt: '2026-08-22' },
    { id: 'question-3', packageRevisionId: 'revision-1', question: 'What compensation do you want?', questionHash: 'h3', normalizedQuestionKey: 'compensation', classification: 'USER_INPUT_REQUIRED', answerStatus: 'USER_INPUT_REQUIRED', createdAt: '2026-08-22', updatedAt: '2026-08-22' },
    { id: 'question-4', packageRevisionId: 'revision-1', question: 'Do you have a disability?', questionHash: 'h4', normalizedQuestionKey: 'disability', classification: 'SENSITIVE_NEVER_AUTOMATIC', answerStatus: 'BLOCKED_SENSITIVE', createdAt: '2026-08-22', updatedAt: '2026-08-22' },
  ],
  artifacts: [
    { id: 'artifact-html', packageRevisionId: 'revision-1', artifactType: 'HTML_PREVIEW', contentType: 'text/html', sizeBytes: 200, sha256Checksum: 'html-hash', templateVersion: 'ats-v1', renderingStatus: 'READY', createdAt: '2026-08-22' },
    { id: 'artifact-pdf', packageRevisionId: 'revision-1', artifactType: 'PDF_RESUME', contentType: 'application/pdf', sizeBytes: 300, sha256Checksum: 'pdf-hash', templateVersion: 'ats-v1', renderingStatus: 'READY', createdAt: '2026-08-22' },
    { id: 'artifact-docx', packageRevisionId: 'revision-1', artifactType: 'DOCX_RESUME', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', sizeBytes: 400, sha256Checksum: 'docx-hash', templateVersion: 'ats-v1', renderingStatus: 'READY', createdAt: '2026-08-22' },
  ],
  warnings: [{ code: 'SHORTENED', message: 'One long bullet was shortened.' }],
  unsupportedRequirements: [{ requirementId: 'requirement-rust', requirement: 'Rust', reason: 'No verified candidate fact supports this technology.' }],
}
const detail: ApplicationPackageDetail = {
  id: 'package-1', jobId: 'job-1', jobTitle: 'Backend Engineer', company: 'Fictional Company', recommendation: 'STRONG_APPLY', status: 'READY', currentRevisionNumber: 1,
  createdAt: '2026-08-22T10:00:00Z', updatedAt: '2026-08-22T10:00:02Z', stale: false, artifactTypes: ['HTML_PREVIEW', 'PDF_RESUME', 'DOCX_RESUME'], createdBy: 'owner', currentRevision: revision,
}
const job: JobPosting = {
  id: 'job-1', sourceType: 'MANUAL', externalId: 'manual-job-1', company: detail.company, title: detail.jobTitle, workplaceType: 'REMOTE', employmentType: 'FULL_TIME',
  descriptionPlainText: 'Build a fictional backend.', descriptionTruncated: false, firstSeenAt: '2026-08-22T10:00:00Z', lastSeenAt: '2026-08-22T10:00:00Z',
  missingSuccessfulRunCount: 0, fingerprint: 'fingerprint', contentHash: 'job-hash', status: 'READY_FOR_EVALUATION', manuallyEdited: false, recordVersion: 0,
  createdAt: '2026-08-22T10:00:00Z', updatedAt: '2026-08-22T10:00:00Z',
}

function detailRoute(value = detail) {
  vi.spyOn(applicationPackagesApi, 'get').mockResolvedValue(value)
  vi.spyOn(applicationPackagesApi, 'revisions').mockResolvedValue([value.currentRevision!])
  vi.spyOn(applicationPackagesApi, 'preview').mockResolvedValue('<html><body><h1>Fictional Candidate</h1><script>evil()</script><img src="https://tracker.test/pixel"><p onclick="evil()">Backend Engineer</p></body></html>')
  return render(<MemoryRouter initialEntries={[`/application-packages/${value.id}`]}><Routes><Route path="/application-packages/:packageId" element={<ApplicationPackageDetailPage />} /></Routes></MemoryRouter>)
}

beforeEach(() => vi.restoreAllMocks())

describe('Phase 5 application-package pages', () => {
  it('renders generating, ready, failed, and stale package-list states', async () => {
    const summaries: ApplicationPackageSummary[] = [
      { ...detail, id: 'generating', status: 'GENERATING', currentRevisionNumber: undefined, artifactTypes: [] },
      detail,
      { ...detail, id: 'failed', jobTitle: 'Platform Engineer', status: 'FAILED', artifactTypes: [] },
      { ...detail, id: 'stale', jobTitle: 'API Engineer', status: 'STALE', stale: true, staleReason: 'Evaluation was replaced.' },
    ]
    vi.spyOn(applicationPackagesApi, 'list').mockResolvedValue({ content: summaries, page: 0, size: 20, totalElements: 4, totalPages: 1 })
    render(<MemoryRouter><ApplicationPackagesPage /></MemoryRouter>)
    expect(await screen.findAllByText('Backend Engineer')).toHaveLength(2)
    expect(screen.getByText('GENERATING')).toBeInTheDocument()
    expect(screen.getByText('FAILED')).toBeInTheDocument()
    expect(screen.getByText('STALE')).toBeInTheDocument()
    expect(screen.getByText('Evaluation was replaced.')).toBeInTheDocument()
    expect(screen.getByText('Draft — not approved or submitted')).toBeInTheDocument()
    expect(screen.getAllByText('PDF')).toHaveLength(2)
  })

  it('shows the persistent draft banner, provenance, warnings, and all question policies', async () => {
    detailRoute()
    expect(await screen.findByText('Draft — not approved or submitted')).toBeInTheDocument()
    expect(screen.getByText(/Rust — No verified candidate fact/)).toBeInTheDocument()
    expect(screen.getByText('VERIFIED_AUTOMATIC')).toBeInTheDocument()
    expect(screen.getByText('SUGGESTED_REQUIRES_REVIEW')).toBeInTheDocument()
    expect(screen.getByText('USER_INPUT_REQUIRED')).toBeInTheDocument()
    expect(screen.getByText('SENSITIVE_NEVER_AUTOMATIC')).toBeInTheDocument()
    expect(screen.getByText(/No answer generated by policy/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Evidence \(1\)/ }))
    expect(await screen.findByText('Built a fictional Java service')).toBeInTheDocument()
    expect(screen.getByText('Candidate fact: fact-verified-1')).toBeInTheDocument()
    expect(screen.getByText('Job requirement: requirement-java')).toBeInTheDocument()
  })

  it('sanitizes the protected HTML preview before rendering it in a sandbox', async () => {
    detailRoute()
    const preview = await screen.findByTitle('Tailored resume preview', {}, { timeout: 5_000 })
    await waitFor(() => expect(preview.getAttribute('srcdoc')).toContain('Fictional Candidate'))
    expect(preview).toHaveAttribute('sandbox', '')
    expect(preview.getAttribute('srcdoc')).not.toMatch(/<script|<img|onclick=|tracker\.test/i)
    expect(preview.getAttribute('srcdoc')).toContain("default-src 'none'")
  })

  it('edits structured content with optimistic locking and marks the result as user edited', async () => {
    detailRoute()
    const updated = { ...content, text: 'User-edited, verified-backend summary.', origin: 'USER_EDITED' as const, verificationStatus: 'UNVERIFIED' as const, userEdited: true, recordVersion: 1 }
    const save = vi.spyOn(applicationPackagesApi, 'updateContent').mockResolvedValue(updated)
    fireEvent.click(await screen.findByRole('button', { name: 'Edit professional summary' }))
    fireEvent.change(screen.getByLabelText('Edit professional summary'), { target: { value: updated.text } })
    fireEvent.click(screen.getByRole('button', { name: 'Save section' }))
    await waitFor(() => expect(save).toHaveBeenCalledWith('package-1', 'content-1', { text: updated.text, recordVersion: 0 }))
    expect(await screen.findByText(updated.text)).toBeInTheDocument()
    expect(screen.getByText('User edited')).toBeInTheDocument()
    expect(screen.getByText('Evidence not verified')).toBeInTheDocument()
    expect(screen.getByText('STALE SOURCES')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download PDF' })).toBeDisabled()
  })

  it('requires an explicit replacement choice before regeneration replaces user edits', async () => {
    const editedRevision = { ...revision, contents: [{ ...content, origin: 'USER_EDITED' as const, userEdited: true }] }
    const editedDetail = { ...detail, currentRevision: editedRevision }
    detailRoute(editedDetail)
    const regenerate = vi.spyOn(applicationPackagesApi, 'regenerate').mockResolvedValue({ ...editedDetail, status: 'GENERATING' })
    fireEvent.click(await screen.findByRole('button', { name: 'Regenerate draft' }))
    expect(screen.getByText(/contains 1 user-edited section/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm replacement to regenerate' })).toBeDisabled()
    fireEvent.click(screen.getByLabelText(/I understand that regeneration will replace my edited content/i))
    fireEvent.change(screen.getByLabelText(/Regeneration reason/i), { target: { value: 'Re-target the role' } })
    fireEvent.click(screen.getByRole('button', { name: 'Replace edits and regenerate' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await waitFor(() => expect(regenerate).toHaveBeenCalledWith('package-1', { replaceUserEdited: true, reason: 'Re-target the role' }, expect.any(String)))
  })

  it('blocks draft generation without a job description before calling the API', async () => {
    vi.spyOn(jobsApi, 'get').mockResolvedValue({ ...job, descriptionPlainText: undefined })
    vi.spyOn(jobsApi, 'duplicates').mockResolvedValue([])
    vi.spyOn(evaluationsApi, 'list').mockResolvedValue([{ id: 'evaluation-4', jobId: job.id, profileVersionId: 'profile-version-7', status: 'SUCCEEDED', stale: false, createdAt: '2026-08-22T09:59:00Z', completedAt: '2026-08-22T10:00:00Z' }])
    const generate = vi.spyOn(applicationPackagesApi, 'generate')
    render(<MemoryRouter initialEntries={['/jobs/job-1']}><Routes><Route path="/jobs/:jobId" element={<JobDetailPage />} /></Routes></MemoryRouter>)
    expect(await screen.findByText(/This job has no description/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Generate application draft' })).toBeDisabled()
    expect(generate).not.toHaveBeenCalled()
  })

  it('uses the authenticated API helper for PDF and DOCX downloads', async () => {
    detailRoute()
    const download = vi.spyOn(applicationPackagesApi, 'download').mockResolvedValue()
    fireEvent.click(await screen.findByRole('button', { name: 'Download PDF' }))
    await waitFor(() => expect(download).toHaveBeenCalledWith('package-1', 'PDF_RESUME'))
    fireEvent.click(screen.getByRole('button', { name: 'Download DOCX' }))
    await waitFor(() => expect(download).toHaveBeenCalledWith('package-1', 'DOCX_RESUME'))
    expect(document.querySelector('a[href*="minio"]')).toBeNull()
  })

  it('keeps a historical revision visibly read-only', async () => {
    vi.spyOn(applicationPackagesApi, 'get').mockResolvedValue(detail)
    vi.spyOn(applicationPackagesApi, 'revision').mockResolvedValue({ ...revision, id: 'revision-old', revisionNumber: 0 })
    render(<MemoryRouter initialEntries={['/application-packages/package-1/revisions/revision-old']}><Routes><Route path="/application-packages/:packageId/revisions/:revisionId" element={<ApplicationPackageRevisionPage />} /></Routes></MemoryRouter>)
    expect(await screen.findByText('HISTORICAL — READ ONLY')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit professional summary' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download PDF' })).toBeDisabled()
  })

  it('renders problem-detail messages from package API failures', async () => {
    vi.spyOn(applicationPackagesApi, 'get').mockRejectedValue({ isAxiosError: true, response: { status: 403, data: { detail: 'This package belongs to another candidate.' } } })
    render(<MemoryRouter initialEntries={['/application-packages/package-1']}><Routes><Route path="/application-packages/:packageId" element={<ApplicationPackageDetailPage />} /></Routes></MemoryRouter>)
    expect(await screen.findByText('This package belongs to another candidate.')).toBeInTheDocument()
  })

  it('generates a draft from job detail and reuses its idempotency key on retry', async () => {
    vi.spyOn(jobsApi, 'get').mockResolvedValue(job)
    vi.spyOn(jobsApi, 'duplicates').mockResolvedValue([])
    vi.spyOn(evaluationsApi, 'list').mockResolvedValue([{ id: 'evaluation-4', jobId: job.id, profileVersionId: 'profile-version-7', status: 'SUCCEEDED', stale: false, createdAt: '2026-08-22T09:59:00Z', completedAt: '2026-08-22T10:00:00Z' }])
    const generate = vi.spyOn(applicationPackagesApi, 'generate').mockRejectedValueOnce(new Error('temporary')).mockResolvedValue(detail)
    render(<MemoryRouter initialEntries={['/jobs/job-1']}><Routes><Route path="/jobs/:jobId" element={<JobDetailPage />} /><Route path="/application-packages/:packageId" element={<div>Created application draft route</div>} /></Routes></MemoryRouter>)
    fireEvent.click(await screen.findByRole('button', { name: 'Generate application draft' }))
    expect(await screen.findByText(/application draft could not be generated/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Generate application draft' }))
    expect(await screen.findByText('Created application draft route')).toBeInTheDocument()
    expect(generate).toHaveBeenCalledTimes(2)
    expect(generate.mock.calls[0][1]).toBe(generate.mock.calls[1][1])
  })
})

describe('Phase 5 API normalization', () => {
  it('normalizes compact backend provenance and 0-100 confidence values', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { ...revision, promptVersion: 'prompt-v2', schemaVersion: 'schema-v2', generationPromptVersion: undefined, generationSchemaVersion: undefined, contents: [{ ...content, type: 'PROFESSIONAL_SUMMARY', key: 'summary', order: 4, contentType: undefined, contentKey: undefined, sectionOrder: undefined }], claims: [{ id: 'claim-raw', contentId: 'content-1', claimText: 'Verified Java work', claimType: 'CANDIDATE_FACT', contentPath: '$.summary', validationStatus: 'VALID', validationCodes: [], candidateFactIds: ['fact-raw'], jobRequirementIds: ['requirement-raw'] }], questions: [{ id: 'q-raw', question: 'Current location?', classification: 'VERIFIED_AUTOMATIC', draftAnswer: 'Noida', answerStatus: 'DRAFTED', confidence: 100 }], artifacts: [{ id: 'a-raw', type: 'PDF_RESUME', contentType: 'application/pdf', sizeBytes: 10, sha256Checksum: 'hash', templateVersion: 'ats-v1', createdAt: '2026-08-22' }] }, headers: {} })
    const normalized = await applicationPackagesApi.revision('package-1', 'revision-1')
    expect(normalized.generationPromptVersion).toBe('prompt-v2')
    expect(normalized.contents[0]).toMatchObject({ contentType: 'PROFESSIONAL_SUMMARY', contentKey: 'summary', sectionOrder: 4 })
    expect(normalized.claims[0].sources).toEqual([{ candidateFactId: 'fact-raw' }, { jobRequirementId: 'requirement-raw' }])
    expect(normalized.questions[0].confidence).toBe(1)
    expect(normalized.artifacts[0]).toMatchObject({ artifactType: 'PDF_RESUME', renderingStatus: 'READY' })
  })
})
