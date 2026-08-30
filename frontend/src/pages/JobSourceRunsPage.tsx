import RefreshIcon from '@mui/icons-material/Refresh'
import { Alert, Button, Chip, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { useCallback, useEffect, useState } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import { apiErrorMessage } from '../api/apiErrors'
import { jobSourcesApi } from '../api/jobSourcesApi'
import type { JobSourceConfiguration, JobSourceRun, JobSourceRunStatus } from '../types/jobs'

export const RUN_POLL_INTERVAL_MS = 5_000
export const RUN_POLL_LIMIT = 12

function runColor(status: JobSourceRunStatus): 'success' | 'warning' | 'error' | 'info' {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'PARTIAL_SUCCESS') return 'warning'
  if (status === 'FAILED') return 'error'
  return 'info'
}

function coverageMeaning(coverage: JobSourceRun['coverage']) {
  if (coverage === 'COMPLETE_INVENTORY') return 'A fully successful run may advance missing-job counters.'
  if (coverage === 'FILTERED_QUERY') return 'This run covers only a filtered or single-page result; unseen jobs are never marked removed.'
  if (coverage === 'PUSH_BATCH') return 'This run contains only the delivered batch; unseen jobs are never marked removed.'
  return ''
}

export function JobSourceRunsPage() {
  const [searchParams] = useSearchParams()
  const [sources, setSources] = useState<JobSourceConfiguration[]>([])
  const [items, setItems] = useState<JobSourceRun[]>([])
  const [sourceId, setSourceId] = useState(searchParams.get('sourceId') ?? '')
  const [status, setStatus] = useState<JobSourceRunStatus | ''>('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [pollAttempt, setPollAttempt] = useState(0)
  const [error, setError] = useState('')
  const [sourcesError, setSourcesError] = useState('')
  const load = useCallback(() => jobSourcesApi.runs({ sourceId: sourceId || undefined, status: status || undefined, page, size: 20 }).then(result => {
      setItems(result.content); setTotalPages(result.totalPages); setError('')
    }).catch(problem => setError(apiErrorMessage(problem, 'Could not load source runs'))).finally(() => setLoading(false)), [page, sourceId, status])
  useEffect(() => {
    jobSourcesApi.list().then(items => { setSources(items); setSourcesError('') }, problem => setSourcesError(apiErrorMessage(problem, 'Could not load sources for filtering')))
  }, [])
  useEffect(() => { void load() }, [load])
  const hasActiveRuns = items.some(run => run.status === 'QUEUED' || run.status === 'RUNNING')
  useEffect(() => {
    if (!hasActiveRuns || pollAttempt >= RUN_POLL_LIMIT) return
    const timer = window.setTimeout(() => { void load().finally(() => setPollAttempt(value => value + 1)) }, RUN_POLL_INTERVAL_MS)
    return () => window.clearTimeout(timer)
  }, [hasActiveRuns, load, pollAttempt])
  const filterSource = (value: string) => { setSourceId(value); setPage(0); setPollAttempt(0) }
  const filterStatus = (value: JobSourceRunStatus | '') => { setStatus(value); setPage(0); setPollAttempt(0) }
  const refresh = () => { setPollAttempt(0); void load() }
  return <Stack spacing={3}>
    <div><Typography variant="h4">Source ingestion runs</Typography><Typography color="text.secondary">Pull synchronizations, filtered searches, and pushed webhook batches share one traceable run history. Active runs refresh for at most one minute.</Typography></div>
    {error && <Alert severity="error">{error}</Alert>}
    {sourcesError && <Alert severity="warning">{sourcesError}</Alert>}
    {hasActiveRuns && pollAttempt >= RUN_POLL_LIMIT && <Alert severity="info">Automatic refresh stopped after {RUN_POLL_LIMIT} attempts.</Alert>}
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
      <TextField select label="Source" value={sourceId} onChange={event => filterSource(event.target.value)} sx={{ minWidth: 220 }}><MenuItem value="">All sources</MenuItem>{sources.map(source => <MenuItem key={source.id} value={source.id}>{source.displayName}</MenuItem>)}</TextField>
      <TextField select label="Run status" value={status} onChange={event => filterStatus(event.target.value as JobSourceRunStatus | '')} sx={{ minWidth: 190 }}><MenuItem value="">All statuses</MenuItem>{(['QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED'] as const).map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>
      <Button variant="outlined" startIcon={<RefreshIcon />} onClick={refresh}>Refresh</Button>
    </Stack>
    {loading ? <Typography>Loading source runs…</Typography> : items.length === 0 ? <Typography color="text.secondary">No ingestion runs match these filters.</Typography> : items.map(run => <Paper key={run.id} variant="outlined" sx={{ p: 2 }}><Stack spacing={1}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between"><Stack direction="row" spacing={1} flexWrap="wrap"><Chip label={run.status} color={runColor(run.status)} /><Chip label={run.triggerType} variant="outlined" />{run.coverage && <Chip label={run.coverage} variant="outlined" color={run.coverage === 'COMPLETE_INVENTORY' ? 'success' : 'default'} />}</Stack><Button component={RouterLink} to={`/job-sources/${run.sourceId}`}>View source</Button></Stack>
      <Typography variant="body2">Started: {run.startedAt ? new Date(run.startedAt).toLocaleString() : 'Waiting'} · Completed: {run.completedAt ? new Date(run.completedAt).toLocaleString() : 'Not completed'}</Typography>
      <Typography variant="body2">Discovered {run.discoveredCount} · Created {run.createdCount} · Updated {run.updatedCount} · Unchanged {run.unchangedCount} · Duplicates {run.duplicateCount} · Failed {run.failedCount} · Removed {run.removedCount}</Typography>
      {run.coverage && <Typography variant="caption" color="text.secondary">{coverageMeaning(run.coverage)}</Typography>}
      {(run.safeErrorCode || run.safeErrorMessage) && <Alert severity={run.status === 'FAILED' ? 'error' : 'warning'}>{run.safeErrorCode && <strong>{run.safeErrorCode}: </strong>}{run.safeErrorMessage || 'The source reported a safe diagnostic code.'}</Alert>}
      {run.errors?.map(problem => <Alert key={problem.id} severity="warning">{problem.externalId && <>Record {problem.externalId}: </>}<strong>{problem.safeErrorCode}</strong> — {problem.safeErrorMessage}</Alert>)}
    </Stack></Paper>)}
    <Stack direction="row" spacing={1} alignItems="center"><Button disabled={page === 0} onClick={() => { setPollAttempt(0); setPage(value => value - 1) }}>Previous</Button><Typography>Page {page + 1} of {Math.max(totalPages, 1)}</Typography><Button disabled={page + 1 >= totalPages} onClick={() => { setPollAttempt(0); setPage(value => value + 1) }}>Next</Button></Stack>
  </Stack>
}
