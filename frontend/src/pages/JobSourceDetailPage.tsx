import { Alert, Button, Chip, Paper, Stack, Typography } from '@mui/material'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { apiErrorMessage, isConflict } from '../api/apiErrors'
import { jobSourcesApi } from '../api/jobSourcesApi'
import { JobSourceForm } from '../components/JobSourceForm'
import { defaultJobSourceInput, sourceToInput, validateJobSource } from '../utils/jobSourceForms'
import type { JobSourceConfiguration, JobSourceInput, JobSourceRun } from '../types/jobs'

export function JobSourceDetailPage() {
  const { sourceId = '' } = useParams()
  const [source, setSource] = useState<JobSourceConfiguration | null>(null)
  const [form, setForm] = useState<JobSourceInput>(defaultJobSourceInput)
  const [runs, setRuns] = useState<JobSourceRun[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [runsError, setRunsError] = useState('')
  const [message, setMessage] = useState('')
  const load = useCallback(() => jobSourcesApi.get(sourceId).then(item => {
      setSource(item); setForm(sourceToInput(item)); setError('')
      return jobSourcesApi.sourceRuns(sourceId, { page: 0, size: 5 }).then(runPage => {
        setRuns(runPage.content); setRunsError('')
      }, problem => {
        setRuns([]); setRunsError(apiErrorMessage(problem, 'Could not load synchronization history'))
      })
    }, problem => {
      setError(apiErrorMessage(problem, 'Could not load the job source')); setRunsError('')
    }).finally(() => setLoading(false)), [sourceId])
  useEffect(() => { void load() }, [load])
  const save = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setMessage('')
    const validation = validateJobSource(form)
    if (validation) { setError(validation); return }
    try {
      setBusy(true)
      const saved = await jobSourcesApi.update(sourceId, form)
      setSource(saved); setForm(sourceToInput(saved)); setMessage('Job source saved')
    } catch (problem) {
      setError(isConflict(problem) ? 'This source changed elsewhere. Reload before saving.' : apiErrorMessage(problem, 'Job source could not be saved'))
    } finally { setBusy(false) }
  }
  const action = async (name: 'enable' | 'disable' | 'archive') => {
    setError(''); setMessage('')
    try { setBusy(true); await jobSourcesApi.action(sourceId, name); setMessage(`Source ${name === 'archive' ? 'archived' : `${name}d`}`); await load() } catch (problem) { setError(apiErrorMessage(problem, 'Source action failed')) } finally { setBusy(false) }
  }
  const sync = async () => {
    setError(''); setMessage('')
    try { setBusy(true); const accepted = await jobSourcesApi.sync(sourceId); setMessage(`Synchronization queued as run ${accepted.runId}`); await load() } catch (problem) { setError(apiErrorMessage(problem, 'Synchronization could not be queued')) } finally { setBusy(false) }
  }
  if (loading) return <Typography>Loading job source…</Typography>
  if (!source) return <Alert severity="error">{error || 'Job source was not found'}</Alert>
  return <Stack spacing={3}>
    <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between"><div><Typography variant="h4">{source.displayName}</Typography><Stack direction="row" spacing={1}><Chip label={source.sourceType} /><Chip label={source.archivedAt ? 'ARCHIVED' : source.enabled ? 'ENABLED' : 'DISABLED'} color={source.enabled && !source.archivedAt ? 'success' : 'default'} /></Stack></div><Button component={RouterLink} to="/job-sources">Back to sources</Button></Stack>
    {error && <Alert severity="error">{error}</Alert>}{message && <Alert severity="success">{message}</Alert>}
    {source.archivedAt && <Alert severity="warning">Archived sources are retained for history and cannot be synchronized.</Alert>}
    <Paper component="form" onSubmit={save} sx={{ p: 3 }}><Stack spacing={2}><JobSourceForm value={form} onChange={setForm} disabled={!!source.archivedAt} /><Button type="submit" variant="contained" disabled={busy || !!source.archivedAt} sx={{ alignSelf: 'flex-start' }}>Save source</Button></Stack></Paper>
    <Stack direction="row" flexWrap="wrap">{!source.archivedAt && (source.enabled ? <Button disabled={busy} onClick={() => void action('disable')}>Disable</Button> : <Button disabled={busy} onClick={() => void action('enable')}>Enable</Button>)}{!source.archivedAt && source.enabled && source.sourceType !== 'EMAIL_WEBHOOK' && <Button disabled={busy} onClick={() => void sync()}>Run sync</Button>}{!source.archivedAt && <Button color="warning" disabled={busy} onClick={() => void action('archive')}>Archive</Button>}<Button component={RouterLink} to={`/job-source-runs?sourceId=${source.id}`}>View all runs</Button></Stack>
    <Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h6" gutterBottom>Recent runs</Typography>{runsError ? <Alert severity="warning">{runsError}</Alert> : runs.length === 0 ? <Typography color="text.secondary">This source has no synchronization runs.</Typography> : runs.map(run => <Stack key={run.id} direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" sx={{ py: 1 }}><div><Chip size="small" label={run.status} /><Typography variant="body2">{run.startedAt ? new Date(run.startedAt).toLocaleString() : new Date(run.createdAt).toLocaleString()}</Typography></div><Typography variant="body2">Created {run.createdCount} · Updated {run.updatedCount} · Duplicates {run.duplicateCount} · Failed {run.failedCount}</Typography></Stack>)}</Paper>
  </Stack>
}
