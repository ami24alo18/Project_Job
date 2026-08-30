import { Alert, Button, Checkbox, Chip, FormControlLabel, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { apiErrorMessage } from '../api/apiErrors'
import { jobSourcesApi } from '../api/jobSourcesApi'
import { JobSourceForm } from '../components/JobSourceForm'
import { CareerSiteOnboarding } from '../components/CareerSiteOnboarding'
import { defaultJobSourceInput, validateJobSource } from '../utils/jobSourceForms'
import type { ExternalJobSourceCreated, ExternalJobSourceInput, JobSourceConfiguration, JobSourceInput, JobSourceRun, JobSpyPolicy } from '../types/jobs'

const defaultExternalInput: ExternalJobSourceInput = { displayName: '', providerIdentifier: '', connectorType: 'JSEARCH', enabled: true }

export function JobSourcesPage() {
  const [sources, setSources] = useState<JobSourceConfiguration[]>([])
  const [runs, setRuns] = useState<JobSourceRun[]>([])
  const [form, setForm] = useState<JobSourceInput>(defaultJobSourceInput)
  const [externalForm, setExternalForm] = useState<ExternalJobSourceInput>(defaultExternalInput)
  const [issued, setIssued] = useState<ExternalJobSourceCreated | null>(null)
  const [jobSpyPolicy, setJobSpyPolicy] = useState<JobSpyPolicy>({ configured: false, allowedSites: [] })
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
  useEffect(() => { void load(); void jobSourcesApi.jobSpyPolicy().then(setJobSpyPolicy).catch(() => setJobSpyPolicy({ configured: false, allowedSites: [] })) }, [load])
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
  const createExternal = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setMessage(''); setIssued(null)
    if (!externalForm.displayName.trim() || !externalForm.providerIdentifier.trim()) { setError('External source display name and identifier are required'); return }
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,199}$/.test(externalForm.providerIdentifier.trim())) { setError('External source identifier may contain letters, numbers, dots, underscores, and hyphens'); return }
    try {
      setBusy('create-external')
      const created = await jobSourcesApi.createExternal({ ...externalForm, displayName: externalForm.displayName.trim(), providerIdentifier: externalForm.providerIdentifier.trim() })
      setExternalForm(defaultExternalInput); setIssued(created); setMessage(`${created.source.displayName} was created`); await load()
    } catch (problem) { setError(apiErrorMessage(problem, 'External job source could not be created')) } finally { setBusy('') }
  }
  const copy = async (value: string) => {
    try { await navigator.clipboard.writeText(value); setMessage('Copied to clipboard') }
    catch { setError('Clipboard access was unavailable; select and copy the value manually') }
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
    <div><Typography variant="h4">Job sources</Typography><Typography color="text.secondary">Configure authorized provider feeds, external automations, and company career sites. Every stored job keeps its ingestion provenance.</Typography></div>
    {error && <Alert severity="error">{error}</Alert>}{runsError && <Alert severity="warning">{runsError}</Alert>}{message && <Alert severity="success">{message}</Alert>}
    <CareerSiteOnboarding onCreated={load} />
    <Paper component="form" onSubmit={createExternal} sx={{ p: 3 }}><Stack spacing={2}><div><Typography variant="h6">Add external automation</Typography><Typography variant="body2" color="text.secondary">Use this for n8n with JSearch, the private JobSpy worker, or another approved workflow that pushes filtered jobs into the application.</Typography></div><Stack direction={{ xs: 'column', md: 'row' }} spacing={2}><TextField required fullWidth label="External source display name" value={externalForm.displayName} onChange={event => setExternalForm(value => ({ ...value, displayName: event.target.value }))} /><TextField required fullWidth label="External source identifier" value={externalForm.providerIdentifier} onChange={event => setExternalForm(value => ({ ...value, providerIdentifier: event.target.value }))} /><TextField select label="Connector" value={externalForm.connectorType} onChange={event => setExternalForm(value => ({ ...value, connectorType: event.target.value as ExternalJobSourceInput['connectorType'] }))} sx={{ minWidth: 190 }}><MenuItem value="JSEARCH">JSearch</MenuItem><MenuItem value="JOBSPY">JobSpy (n8n worker)</MenuItem><MenuItem value="CUSTOM_WEBHOOK">Custom webhook</MenuItem></TextField></Stack>{externalForm.connectorType === 'JOBSPY' && <Alert severity={jobSpyPolicy.configured ? 'info' : 'warning'}>{jobSpyPolicy.configured ? <>Deployment-approved boards: <strong>{jobSpyPolicy.allowedSites.join(', ')}</strong>. JobSpy requests are orchestrated by the separate n8n workflow and remain push-only.</> : <>No JobSpy boards are currently enabled. Set <code>JOBSPY_ALLOWED_SITES</code> only after board authorization review.</>}</Alert>}<FormControlLabel control={<Checkbox checked={externalForm.enabled} onChange={event => setExternalForm(value => ({ ...value, enabled: event.target.checked }))} />} label="Enabled after creation" /><Button type="submit" variant="contained" disabled={busy === 'create-external'} sx={{ alignSelf: 'flex-start' }}>Create external source</Button></Stack></Paper>
    {issued && <Alert severity="warning"><Stack spacing={1}><strong>Copy the webhook token now. It is shown only once.</strong><TextField label="Webhook token (shown once)" value={issued.webhookToken} slotProps={{ input: { readOnly: true } }} /><Typography variant="body2"><code>{issued.webhookUrl}</code></Typography><Stack direction="row"><Button onClick={() => void copy(issued.webhookToken)}>Copy token</Button><Button onClick={() => void copy(issued.webhookUrl)}>Copy endpoint</Button></Stack></Stack></Alert>}
    <Paper component="form" onSubmit={create} sx={{ p: 3 }}><Stack spacing={2}><Typography variant="h6">Add provider feed or email source</Typography><JobSourceForm value={form} onChange={setForm} includeEnabled /><Button type="submit" variant="contained" disabled={busy === 'create'} sx={{ alignSelf: 'flex-start' }}>Add source</Button></Stack></Paper>
    {sources.length === 0 ? <Typography color="text.secondary">No job sources configured.</Typography> : sources.map(source => {
      const latest = latestBySource.get(source.id)
      return <Paper key={source.id} variant="outlined" sx={{ p: 2 }}><Stack spacing={2}>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
          <div><Stack direction="row" spacing={1} flexWrap="wrap"><Typography variant="h6">{source.displayName}</Typography><Chip size="small" label={source.sourceType} />{source.connectorType && <Chip size="small" variant="outlined" label={source.connectorType} />}{source.sourceCategory && <Chip size="small" variant="outlined" label={source.sourceCategory} />}{source.supportStatus && source.supportStatus !== 'SUPPORTED' && <Chip size="small" color="warning" label={source.supportStatus} />}{source.archivedAt ? <Chip size="small" color="default" label="ARCHIVED" /> : <Chip size="small" color={source.enabled ? 'success' : 'warning'} label={source.enabled ? 'ENABLED' : 'DISABLED'} />}{source.consecutiveFailureCount > 0 && <Chip size="small" color="error" label={`${source.consecutiveFailureCount} consecutive failure${source.consecutiveFailureCount === 1 ? '' : 's'}`} />}</Stack><Typography variant="body2" color="text.secondary">Identifier: {source.providerIdentifier}</Typography><Typography variant="body2">Last successful ingestion: {source.lastSuccessfulSyncAt ? new Date(source.lastSuccessfulSyncAt).toLocaleString() : 'Never'}</Typography></div>
          <Stack direction="row" flexWrap="wrap"><Button component={RouterLink} to={`/job-sources/${source.id}`}>Details</Button>{!source.archivedAt && (source.enabled ? <Button disabled={!!busy} onClick={() => void action(source, 'disable')}>Disable</Button> : source.supportStatus === 'SUPPORTED' || !source.supportStatus ? <Button disabled={!!busy} onClick={() => void action(source, 'enable')}>Enable</Button> : null)}{!source.archivedAt && source.enabled && (source.sourceCategory === 'PULL_FEED' || (!source.sourceCategory && (source.sourceType === 'LEVER' || source.sourceType === 'GREENHOUSE'))) && <Button disabled={!!busy} onClick={() => void sync(source)}>Run sync</Button>}{!source.archivedAt && <Button color="warning" disabled={!!busy} onClick={() => void action(source, 'archive')}>Archive</Button>}</Stack>
        </Stack>
        {latest && <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center"><Chip size="small" label={`Last run: ${latest.status}`} color={latest.status === 'FAILED' ? 'error' : latest.status === 'PARTIAL_SUCCESS' ? 'warning' : latest.status === 'SUCCEEDED' ? 'success' : 'info'} />{latest.coverage && <Chip size="small" variant="outlined" label={latest.coverage} />}<Typography variant="body2">Created {latest.createdCount} · Updated {latest.updatedCount} · Unchanged {latest.unchangedCount} · Duplicates {latest.duplicateCount} · Failed {latest.failedCount}</Typography></Stack>}
      </Stack></Paper>
    })}
  </Stack>
}
