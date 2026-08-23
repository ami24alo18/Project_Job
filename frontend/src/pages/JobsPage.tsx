import { Alert, Button, Checkbox, Chip, FormControlLabel, Grid, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { apiErrorMessage } from '../api/apiErrors'
import { jobsApi } from '../api/jobsApi'
import type { EmploymentType, JobListFilters, JobPosting, JobPostingStatus, JobSourceType, WorkplaceType } from '../types/jobs'

type FilterForm = Required<Pick<JobListFilters, 'includeDuplicates' | 'includeArchived'>> & {
  sourceId: string; sourceType: JobSourceType | ''; status: JobPostingStatus | ''; company: string; title: string; location: string
  workplaceType: WorkplaceType | ''; employmentType: EmploymentType | ''; publishedFrom: string; publishedTo: string; firstSeenFrom: string; firstSeenTo: string
}
const emptyFilters: FilterForm = { sourceId: '', sourceType: '', status: '', company: '', title: '', location: '', workplaceType: '', employmentType: '', publishedFrom: '', publishedTo: '', firstSeenFrom: '', firstSeenTo: '', includeDuplicates: false, includeArchived: false }
const statuses: JobPostingStatus[] = ['READY_FOR_EVALUATION', 'NEEDS_REVIEW', 'DUPLICATE', 'EXPIRED', 'SOURCE_REMOVED', 'ARCHIVED']
const sourceTypes: JobSourceType[] = ['LEVER', 'GREENHOUSE', 'EMAIL_WEBHOOK', 'MANUAL']
const workplaces: WorkplaceType[] = ['REMOTE', 'HYBRID', 'ONSITE', 'UNSPECIFIED']
const employments: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'TEMPORARY', 'INTERNSHIP', 'OTHER', 'UNSPECIFIED']

function toParams(form: FilterForm): JobListFilters {
  const start = (value: string) => value ? `${value}T00:00:00.000Z` : undefined
  const end = (value: string) => value ? `${value}T23:59:59.999Z` : undefined
  return {
    sourceId: form.sourceId || undefined, sourceType: form.sourceType || undefined, status: form.status || undefined,
    company: form.company || undefined, title: form.title || undefined, location: form.location || undefined,
    workplaceType: form.workplaceType || undefined, employmentType: form.employmentType || undefined,
    publishedFrom: start(form.publishedFrom), publishedTo: end(form.publishedTo),
    firstSeenFrom: start(form.firstSeenFrom), firstSeenTo: end(form.firstSeenTo),
    includeDuplicates: form.includeDuplicates, includeArchived: form.includeArchived,
  }
}

export function JobsPage() {
  const [draft, setDraft] = useState<FilterForm>(emptyFilters)
  const [filters, setFilters] = useState<JobListFilters>(() => toParams(emptyFilters))
  const [items, setItems] = useState<JobPosting[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = useCallback(() => jobsApi.list({ ...filters, page, size: 20, sort: 'firstSeenAt,desc' }).then(result => {
      setItems(result.content); setTotalPages(result.totalPages); setTotalElements(result.totalElements); setError('')
    }).catch(problem => setError(apiErrorMessage(problem, 'Could not load jobs'))).finally(() => setLoading(false)), [filters, page])
  useEffect(() => { void load() }, [load])
  const set = <K extends keyof FilterForm>(key: K, value: FilterForm[K]) => setDraft(current => ({ ...current, [key]: value }))
  const apply = (event: FormEvent) => { event.preventDefault(); setPage(0); setLoading(true); setFilters(toParams(draft)) }
  const reset = () => { setDraft(emptyFilters); setPage(0); setLoading(true); setFilters(toParams(emptyFilters)) }
  return <Stack spacing={3}>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between"><div><Typography variant="h4">Discovered jobs</Typography><Typography color="text.secondary">Normalized jobs from authorized feeds, email alerts, and manual entry.</Typography></div><Button component={RouterLink} to="/jobs/new" variant="contained">Add manual job</Button></Stack>
    {error && <Alert severity="error">{error}</Alert>}
    <Paper component="form" onSubmit={apply} sx={{ p: 2 }}><Grid container spacing={2}>
      <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Title search" value={draft.title} onChange={event => set('title', event.target.value)} /></Grid>
      <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Company" value={draft.company} onChange={event => set('company', event.target.value)} /></Grid>
      <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Location" value={draft.location} onChange={event => set('location', event.target.value)} /></Grid>
      <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth label="Source ID" value={draft.sourceId} onChange={event => set('sourceId', event.target.value)} /></Grid>
      <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField select fullWidth label="Source type" value={draft.sourceType} onChange={event => set('sourceType', event.target.value as JobSourceType | '')}><MenuItem value="">All</MenuItem>{sourceTypes.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField></Grid>
      <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField select fullWidth label="Job status" value={draft.status} onChange={event => set('status', event.target.value as JobPostingStatus | '')}><MenuItem value="">All active statuses</MenuItem>{statuses.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField></Grid>
      <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField select fullWidth label="Workplace type" value={draft.workplaceType} onChange={event => set('workplaceType', event.target.value as WorkplaceType | '')}><MenuItem value="">All</MenuItem>{workplaces.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField></Grid>
      <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField select fullWidth label="Employment type" value={draft.employmentType} onChange={event => set('employmentType', event.target.value as EmploymentType | '')}><MenuItem value="">All</MenuItem>{employments.map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField></Grid>
      {([['publishedFrom', 'Published from'], ['publishedTo', 'Published to'], ['firstSeenFrom', 'First seen from'], ['firstSeenTo', 'First seen to']] as const).map(([key, label]) => <Grid key={key} size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth type="date" label={label} value={draft[key]} onChange={event => set(key, event.target.value)} slotProps={{ inputLabel: { shrink: true } }} /></Grid>)}
      <Grid size={12}><FormControlLabel control={<Checkbox checked={draft.includeDuplicates} onChange={event => set('includeDuplicates', event.target.checked)} />} label="Include duplicates" /><FormControlLabel control={<Checkbox checked={draft.includeArchived} onChange={event => set('includeArchived', event.target.checked)} />} label="Include archived jobs" /></Grid>
      <Grid size={12}><Button type="submit" variant="contained">Apply filters</Button><Button onClick={reset}>Reset</Button></Grid>
    </Grid></Paper>
    <Typography variant="body2" color="text.secondary">{totalElements} matching job{totalElements === 1 ? '' : 's'}. Duplicates and archived jobs are excluded by default.</Typography>
    {loading ? <Typography>Loading jobs…</Typography> : items.length === 0 ? <Typography color="text.secondary">No jobs match these filters.</Typography> : <TableContainer component={Paper} variant="outlined"><Table><TableHead><TableRow><TableCell>Job</TableCell><TableCell>Location</TableCell><TableCell>Source</TableCell><TableCell>Status</TableCell><TableCell>First seen</TableCell><TableCell>Published</TableCell></TableRow></TableHead><TableBody>{items.map(job => <TableRow key={job.id} hover><TableCell><Button component={RouterLink} to={`/jobs/${job.id}`} sx={{ p: 0, justifyContent: 'flex-start', textAlign: 'left' }}>{job.title}</Button><Typography variant="body2">{job.company}</Typography></TableCell><TableCell>{job.location || 'Unspecified'}<Typography variant="caption" display="block">{job.workplaceType} · {job.employmentType}</Typography></TableCell><TableCell><Chip size="small" label={job.sourceType} /></TableCell><TableCell><Chip size="small" label={job.status} color={job.status === 'DUPLICATE' ? 'warning' : job.status === 'READY_FOR_EVALUATION' ? 'success' : 'default'} />{job.duplicateOfJobId && <Typography variant="caption" display="block">Duplicate record</Typography>}</TableCell><TableCell>{new Date(job.firstSeenAt).toLocaleDateString()}</TableCell><TableCell>{job.publishedAt ? new Date(job.publishedAt).toLocaleDateString() : 'Unknown'}</TableCell></TableRow>)}</TableBody></Table></TableContainer>}
    <Stack direction="row" spacing={1} alignItems="center"><Button disabled={page === 0} onClick={() => { setLoading(true); setPage(value => value - 1) }}>Previous</Button><Typography>Page {page + 1} of {Math.max(totalPages, 1)}</Typography><Button disabled={page + 1 >= totalPages} onClick={() => { setLoading(true); setPage(value => value + 1) }}>Next</Button></Stack>
  </Stack>
}
