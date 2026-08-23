import { Alert, Button, Chip, Divider, Grid, Paper, Stack, Typography } from '@mui/material'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom'
import { apiErrorMessage, isConflict } from '../api/apiErrors'
import { applicationPackagesApi, createApplicationPackageIdempotencyKey } from '../api/applicationPackagesApi'
import { jobsApi } from '../api/jobsApi'
import { JobFields } from '../components/JobFields'
import { SafeExternalLink } from '../components/SafeExternalLink'
import type { JobPosting } from '../types/jobs'
import { jobFormToInput, jobToForm, validateJobForm, type JobFormState } from '../utils/jobForms'

export function JobDetailPage() {
  const { jobId = '' } = useParams()
  const navigate = useNavigate()
  const [job, setJob] = useState<JobPosting | null>(null)
  const [duplicates, setDuplicates] = useState<JobPosting[]>([])
  const [form, setForm] = useState<JobFormState | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [duplicatesError, setDuplicatesError] = useState('')
  const [message, setMessage] = useState('')
  const generationRequest = useRef<{ jobId: string; key: string } | null>(null)
  const load = useCallback(() => jobsApi.get(jobId).then(item => {
      setJob(item); setForm(null); setError('')
      return jobsApi.duplicates(jobId).then(related => {
        setDuplicates(related); setDuplicatesError('')
      }, problem => {
        setDuplicates([]); setDuplicatesError(apiErrorMessage(problem, 'Could not load duplicate relationships'))
      })
    }, problem => {
      setError(apiErrorMessage(problem, 'Could not load this job')); setDuplicatesError('')
    }).finally(() => setLoading(false)), [jobId])
  useEffect(() => { void load() }, [load])
  const save = async (event: FormEvent) => {
    event.preventDefault(); if (!job || !form) return
    setError(''); setMessage('')
    const validation = validateJobForm(form)
    if (validation) { setError(validation); return }
    try {
      setBusy(true)
      const saved = await jobsApi.update(job.id, jobFormToInput(form, job.recordVersion))
      setJob(saved); setForm(null); setMessage(saved.sourceType === 'MANUAL' ? 'Manual job saved' : 'Manual correction saved; provider refreshes will not overwrite it')
    } catch (problem) { setError(isConflict(problem) ? 'This job changed elsewhere. Reload before saving.' : apiErrorMessage(problem, 'Job could not be saved')) } finally { setBusy(false) }
  }
  const action = async (name: 'archive' | 'restore' | 'mark-expired') => {
    if (!job) return
    setError(''); setMessage('')
    try { setBusy(true); const saved = await jobsApi.action(job.id, name); setJob(saved); setMessage(`Job status changed to ${saved.status}`) } catch (problem) { setError(apiErrorMessage(problem, 'Job action failed')) } finally { setBusy(false) }
  }
  const generateApplicationDraft = async () => {
    setError(''); setMessage(''); setBusy(true)
    try {
      if (generationRequest.current?.jobId !== jobId) generationRequest.current = { jobId, key: createApplicationPackageIdempotencyKey() }
      const created = await applicationPackagesApi.generate(jobId, generationRequest.current.key)
      generationRequest.current = null
      navigate(`/application-packages/${created.id}`)
    } catch (problem) {
      setError(apiErrorMessage(problem, 'An application draft could not be generated. Confirm that a completed evaluation and published profile version are available.'))
    } finally { setBusy(false) }
  }
  if (loading) return <Typography>Loading job…</Typography>
  if (!job) return <Alert severity="error">{error || 'Job was not found'}</Alert>
  return <Stack spacing={3}>
    <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}><div><Typography variant="h4">{job.title}</Typography><Typography variant="h6" color="text.secondary">{job.company}</Typography><Stack direction="row" spacing={1} flexWrap="wrap"><Chip label={job.status} color={job.status === 'READY_FOR_EVALUATION' ? 'success' : job.status === 'DUPLICATE' ? 'warning' : 'default'} /><Chip label={job.sourceType} variant="outlined" />{job.manuallyEdited && <Chip label="MANUALLY EDITED" color="info" />}{job.descriptionTruncated && <Chip label="DESCRIPTION TRUNCATED" color="warning" />}</Stack></div><Button component={RouterLink} to="/jobs">Back to jobs</Button></Stack>
    {error && <Alert severity="error">{error}</Alert>}{message && <Alert severity="success">{message}</Alert>}
    {job.sourceUpdateAvailable && <Alert severity="warning">A provider content update is available. Your manual corrections were preserved.</Alert>}
    {job.duplicateOfJobId && <Alert severity="warning">This job is an exact duplicate of <Button component={RouterLink} to={`/jobs/${job.duplicateOfJobId}`}>job {job.duplicateOfJobId}</Button>.</Alert>}
    {duplicatesError && <Alert severity="warning">{duplicatesError}</Alert>}
    {form ? <Paper component="form" onSubmit={save} sx={{ p: 3 }}><Stack spacing={2}>{job.sourceType !== 'MANUAL' && <Alert severity="warning">Saving a provider job creates a manual correction. Later provider refreshes will not overwrite it.</Alert>}<JobFields value={form} onChange={setForm} /><Stack direction="row"><Button type="submit" variant="contained" disabled={busy}>Save changes</Button><Button disabled={busy} onClick={() => setForm(null)}>Cancel</Button></Stack></Stack></Paper> : <>
      <Paper variant="outlined" sx={{ p: 3 }}><Grid container spacing={2}><Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Location</Typography><Typography>{job.location || 'Unspecified'}{job.countryCode ? `, ${job.countryCode}` : ''}</Typography></Grid><Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Workplace</Typography><Typography>{job.workplaceType}</Typography></Grid><Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Employment</Typography><Typography>{job.employmentType}</Typography></Grid><Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Department / team</Typography><Typography>{[job.department, job.team].filter(Boolean).join(' · ') || 'Unspecified'}</Typography></Grid><Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Salary</Typography><Typography>{job.salaryMinimum ?? 'Unknown'}{job.salaryMaximum !== undefined ? ` – ${job.salaryMaximum}` : ''} {job.salaryCurrency || ''} {job.salaryInterval ? `/ ${job.salaryInterval}` : ''}</Typography></Grid><Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Links</Typography><Stack><SafeExternalLink href={job.applyUrl}>Open official application</SafeExternalLink><SafeExternalLink href={job.sourceUrl}>Open source posting</SafeExternalLink></Stack></Grid></Grid>
      <Divider sx={{ my: 3 }} /><Typography variant="h6">Description</Typography>{job.descriptionPlainText ? <Typography component="pre" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontFamily: 'inherit', m: 0, mt: 1 }}>{job.descriptionPlainText}</Typography> : <Typography color="text.secondary">No description was supplied.</Typography>}</Paper>
      <Paper variant="outlined" sx={{ p: 3 }}><Typography variant="h6" gutterBottom>Source metadata</Typography><Typography>External ID: {job.externalId}</Typography>{job.sourceId && <Typography>Source ID: <Button component={RouterLink} to={`/job-sources/${job.sourceId}`}>{job.sourceId}</Button></Typography>}<Typography>First seen: {new Date(job.firstSeenAt).toLocaleString()}</Typography><Typography>Last seen: {new Date(job.lastSeenAt).toLocaleString()}</Typography><Typography>Published: {job.publishedAt ? new Date(job.publishedAt).toLocaleString() : 'Unknown'}</Typography><Typography>Source updated: {job.sourceUpdatedAt ? new Date(job.sourceUpdatedAt).toLocaleString() : 'Unknown'}</Typography><Typography>Expires: {job.expiresAt ? new Date(job.expiresAt).toLocaleString() : 'No trusted expiry supplied'}</Typography></Paper>
    </>}
    <Stack direction="row" flexWrap="wrap"><Button variant="contained" disabled={busy || !!form} onClick={() => setForm(jobToForm(job))}>Edit job</Button><Button variant="contained" color="secondary" disabled={busy || !!form || !['READY_FOR_EVALUATION', 'NEEDS_REVIEW'].includes(job.status)} onClick={() => void generateApplicationDraft()}>Generate application draft</Button>{job.status === 'ARCHIVED' ? <Button disabled={busy} onClick={() => void action('restore')}>Restore</Button> : <Button color="warning" disabled={busy} onClick={() => void action('archive')}>Archive</Button>}{job.status !== 'ARCHIVED' && job.status !== 'EXPIRED' && <Button disabled={busy} onClick={() => void action('mark-expired')}>Mark expired</Button>}</Stack>
    {duplicates.length > 0 && <Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h6">Related duplicate records</Typography>{duplicates.map(item => <Button key={item.id} component={RouterLink} to={`/jobs/${item.id}`}>{item.company} — {item.title}</Button>)}</Paper>}
  </Stack>
}
