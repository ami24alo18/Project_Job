import { Alert, Button, Chip, Paper, Stack, Typography } from '@mui/material'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { apiErrorMessage } from '../api/apiErrors'
import { jobSourcesApi } from '../api/jobSourcesApi'
import { JobSourceForm } from '../components/JobSourceForm'
import { defaultJobSourceInput, validateJobSource } from '../utils/jobSourceForms'
import type { JobSourceConfiguration, JobSourceInput, JobSourceRun } from '../types/jobs'

export function JobSourcesPage() {
  const [sources, setSources] = useState<JobSourceConfiguration[]>([])
  const [runs, setRuns] = useState<JobSourceRun[]>([])
  const [form, setForm] = useState<JobSourceInput>(defaultJobSourceInput)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [runsError, setRunsError] = useState('')
  const [message, setMessage] = useState('')
  const load = useCallback(() => jobSourcesApi.list().then(items => {
      setSources(items)
      setError('')
      return Promise.allSettled(items.map(item => jobSourcesApi.sourceRuns(item.id, { page: 0, size: 1 }))).then(runPages => {
        setRuns(runPages.flatMap(result => result.status === 'fulfilled' ? result.value.content.slice(0, 1) : []))
        const failedNames = runPages.flatMap((result, index) => result.status === 'rejected' ? [items[index].displayName] : [])
        setRunsError(failedNames.length ? `Could not load the latest synchronization status for ${failedNames.join(', ')}.` : '')
      })
    }).catch(problem => {
      setError(apiErrorMessage(problem, 'Could not load job sources'))
      setRunsError('')
    }).finally(() => setLoading(false)), [])
  useEffect(() => { void load() }, [load])
  const latestBySource = useMemo(() => {
    const result = new Map<string, JobSourceRun>()
    runs.forEach(run => { if (!result.has(run.sourceId)) result.set(run.sourceId, run) })
    return result
  }, [runs])
  const create = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setMessage('')
    const validation = validateJobSource(form)
    if (validation) { setError(validation); return }
    try {
      setBusy('create')
      const created = await jobSourcesApi.create(form)
      setForm(defaultJobSourceInput)
      setMessage(`${created.displayName} was created`)
      await load()
    } catch (problem) {
      setError(apiErrorMessage(problem, 'Job source could not be created; it may duplicate an active source'))
    } finally { setBusy('') }
  }
  const action = async (source: JobSourceConfiguration, name: 'enable' | 'disable' | 'archive') => {
    setError(''); setMessage(''); setBusy(`${source.id}-${name}`)
    try {
      await jobSourcesApi.action(source.id, name)
      setMessage(`${source.displayName} was ${name === 'archive' ? 'archived' : `${name}d`}`)
      await load()
    } catch (problem) { setError(apiErrorMessage(problem, 'Source action failed')) } finally { setBusy('') }
  }
  const sync = async (source: JobSourceConfiguration) => {
    setError(''); setMessage(''); setBusy(`${source.id}-sync`)
    try {
      const accepted = await jobSourcesApi.sync(source.id)
      setMessage(`Synchronization queued as run ${accepted.runId}`)
    } catch (problem) { setError(apiErrorMessage(problem, 'Synchronization could not be queued')) } finally { setBusy('') }
  }
  if (loading) return <Typography>Loading job sources…</Typography>
  return <Stack spacing={3}>
    <div><Typography variant="h4">Job sources</Typography><Typography color="text.secondary">Configure authorized public feeds and logical email-alert sources. Arbitrary fetch URLs are never accepted.</Typography></div>
    {error && <Alert severity="error">{error}</Alert>}{runsError && <Alert severity="warning">{runsError}</Alert>}{message && <Alert severity="success">{message}</Alert>}
    <Paper component="form" onSubmit={create} sx={{ p: 3 }}><Stack spacing={2}><Typography variant="h6">Add an authorized source</Typography><JobSourceForm value={form} onChange={setForm} includeEnabled /><Button type="submit" variant="contained" disabled={busy === 'create'} sx={{ alignSelf: 'flex-start' }}>Add source</Button></Stack></Paper>
    {sources.length === 0 ? <Typography color="text.secondary">No job sources configured.</Typography> : sources.map(source => {
      const latest = latestBySource.get(source.id)
      return <Paper key={source.id} variant="outlined" sx={{ p: 2 }}><Stack spacing={2}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
          <div><Stack direction="row" spacing={1} flexWrap="wrap"><Typography variant="h6">{source.displayName}</Typography><Chip size="small" label={source.sourceType} /><Chip size="small" variant="outlined" label={source.region} />{source.archivedAt ? <Chip size="small" color="default" label="ARCHIVED" /> : <Chip size="small" color={source.enabled ? 'success' : 'warning'} label={source.enabled ? 'ENABLED' : 'DISABLED'} />}{source.consecutiveFailureCount > 0 && <Chip size="small" color="error" label={`${source.consecutiveFailureCount} consecutive failure${source.consecutiveFailureCount === 1 ? '' : 's'}`} />}</Stack><Typography variant="body2" color="text.secondary">Identifier: {source.providerIdentifier}</Typography><Typography variant="body2">Last successful sync: {source.lastSuccessfulSyncAt ? new Date(source.lastSuccessfulSyncAt).toLocaleString() : 'Never'}</Typography></div>
          <Stack direction="row" flexWrap="wrap"><Button component={RouterLink} to={`/job-sources/${source.id}`}>Details</Button>{!source.archivedAt && (source.enabled ? <Button disabled={!!busy} onClick={() => void action(source, 'disable')}>Disable</Button> : <Button disabled={!!busy} onClick={() => void action(source, 'enable')}>Enable</Button>)}{!source.archivedAt && source.enabled && source.sourceType !== 'EMAIL_WEBHOOK' && <Button disabled={!!busy} onClick={() => void sync(source)}>Run sync</Button>}{!source.archivedAt && <Button color="warning" disabled={!!busy} onClick={() => void action(source, 'archive')}>Archive</Button>}</Stack>
        </Stack>
        {latest && <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center"><Chip size="small" label={`Last run: ${latest.status}`} color={latest.status === 'FAILED' ? 'error' : latest.status === 'PARTIAL_SUCCESS' ? 'warning' : latest.status === 'SUCCEEDED' ? 'success' : 'info'} /><Typography variant="body2">Created {latest.createdCount} · Updated {latest.updatedCount} · Unchanged {latest.unchangedCount} · Duplicates {latest.duplicateCount} · Failed {latest.failedCount}</Typography></Stack>}
      </Stack></Paper>
    })}
  </Stack>
}
