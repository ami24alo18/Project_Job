import { Alert, Button, Chip, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { useCallback, useEffect, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { applicationPackagesApi } from '../api/applicationPackagesApi'
import { apiErrorMessage } from '../api/apiErrors'
import { ApplicationDraftBanner } from '../components/ApplicationDraftBanner'
import type { ApplicationPackagePage, ApplicationPackageStatus } from '../types/applicationPackages'

const statuses: ApplicationPackageStatus[] = ['REQUESTED', 'GENERATING', 'READY', 'FAILED', 'STALE', 'ARCHIVED']
const emptyPage: ApplicationPackagePage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

function statusColor(status: ApplicationPackageStatus): 'default' | 'info' | 'success' | 'error' | 'warning' {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'STALE') return 'warning'
  if (status === 'GENERATING' || status === 'REQUESTED') return 'info'
  return 'default'
}

export function ApplicationPackagesPage() {
  const [result, setResult] = useState(emptyPage)
  const [status, setStatus] = useState<ApplicationPackageStatus | ''>('')
  const [staleOnly, setStaleOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(() => applicationPackagesApi.list({ status: status || undefined, stale: staleOnly || undefined, page, size: 20, sort: 'updatedAt,desc' })
    .then(setResult, problem => setError(apiErrorMessage(problem, 'Application packages could not be loaded')))
    .finally(() => setLoading(false)), [page, staleOnly, status])
  useEffect(() => { void load() }, [load])
  return <Stack spacing={3}>
    <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}><div><Typography variant="h4">Application packages</Typography><Typography color="text.secondary">Traceable, fact-grounded resume and application-content revisions.</Typography></div><Button onClick={() => { setLoading(true); setError(''); void load() }}>Refresh</Button></Stack>
    <ApplicationDraftBanner />
    {error && <Alert severity="error" action={<Button color="inherit" onClick={() => { setLoading(true); setError(''); void load() }}>Retry</Button>}>{error}</Alert>}
    <Paper variant="outlined" sx={{ p: 2 }}><Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
      <TextField select label="Package status" value={status} onChange={event => { setStatus(event.target.value as ApplicationPackageStatus | ''); setPage(0) }} sx={{ minWidth: 210 }}><MenuItem value="">All statuses</MenuItem>{statuses.map(value => <MenuItem value={value} key={value}>{value}</MenuItem>)}</TextField>
      <Button variant={staleOnly ? 'contained' : 'outlined'} color="warning" onClick={() => { setStaleOnly(value => !value); setPage(0) }}>{staleOnly ? 'Showing stale only' : 'Show stale only'}</Button>
    </Stack></Paper>
    {loading ? <Typography>Loading application packages…</Typography> : result.content.length === 0 ? <Alert severity="info">No application-package drafts match these filters. Generate one from an eligible job detail page.</Alert> : result.content.map(item => <Paper key={item.id} variant="outlined" sx={{ p: 2 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
        <Stack spacing={1}>
          <div><Typography variant="h6">{item.jobTitle}</Typography><Typography color="text.secondary">{item.company}</Typography></div>
          <Stack direction="row" spacing={1} flexWrap="wrap"><Chip size="small" label={item.status} color={statusColor(item.status)} />{item.recommendation && <Chip size="small" variant="outlined" label={item.recommendation} />}{item.stale && <Chip size="small" color="warning" label="STALE SOURCES" />}{(item.artifactTypes ?? []).map(type => <Chip size="small" variant="outlined" key={type} label={type.replace('_RESUME', '')} />)}</Stack>
          {item.stale && <Typography variant="body2" color="warning.main">{item.staleReason || 'One or more immutable source references are no longer current.'}</Typography>}
          <Typography variant="body2">Current revision: {item.currentRevisionNumber ?? 'Pending'} · Created {new Date(item.createdAt).toLocaleString()}</Typography>
        </Stack>
        <Button component={RouterLink} to={`/application-packages/${item.id}`} variant="contained">Open draft</Button>
      </Stack>
    </Paper>)}
    <Stack direction="row" spacing={1} alignItems="center"><Button disabled={page === 0 || loading} onClick={() => setPage(value => value - 1)}>Previous</Button><Typography>Page {page + 1} of {Math.max(result.totalPages, 1)}</Typography><Button disabled={page + 1 >= result.totalPages || loading} onClick={() => setPage(value => value + 1)}>Next</Button></Stack>
  </Stack>
}
