import { Alert, Button, Chip, Stack, Typography } from '@mui/material'
import { useCallback, useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { applicationPackagesApi } from '../api/applicationPackagesApi'
import { apiErrorMessage } from '../api/apiErrors'
import { ApplicationDraftBanner } from '../components/ApplicationDraftBanner'
import { ApplicationRevisionInspector } from '../components/ApplicationRevisionInspector'
import type { ApplicationPackageDetail, ApplicationPackageRevision } from '../types/applicationPackages'

export function ApplicationPackageRevisionPage() {
  const { packageId = '', revisionId = '' } = useParams()
  const [item, setItem] = useState<ApplicationPackageDetail | null>(null)
  const [revision, setRevision] = useState<ApplicationPackageRevision | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const load = useCallback(() => Promise.all([applicationPackagesApi.get(packageId), applicationPackagesApi.revision(packageId, revisionId)])
    .then(([detail, selected]) => { setItem(detail); setRevision(selected) }, problem => setError(apiErrorMessage(problem, 'This application-package revision could not be loaded')))
    .finally(() => setLoading(false)), [packageId, revisionId])
  useEffect(() => { void load() }, [load])
  if (loading) return <Typography>Loading historical draft revision…</Typography>
  if (!item || !revision) return <Alert severity="error" action={<Button onClick={() => { setLoading(true); setError(''); void load() }}>Retry</Button>}>{error || 'Revision not found'}</Alert>
  const current = item.currentRevision?.id === revision.id
  return <Stack spacing={3}>
    <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}><div><Typography variant="h4">{item.jobTitle} — revision {revision.revisionNumber}</Typography><Typography variant="h6" color="text.secondary">{item.company}</Typography></div><Button component={RouterLink} to={`/application-packages/${packageId}`}>Back to current draft</Button></Stack>
    <ApplicationDraftBanner />
    <Stack direction="row" spacing={1}><Chip label={revision.status} color={revision.status === 'READY' ? 'success' : revision.status === 'FAILED' ? 'error' : 'warning'} />{current ? <Chip color="info" label="CURRENT REVISION" /> : <Chip variant="outlined" label="HISTORICAL — READ ONLY" />}</Stack>
    {!current && <Alert severity="info">Historical provenance is immutable. Edit and protected download actions are available only from the current package draft.</Alert>}
    <ApplicationRevisionInspector packageId={packageId} revision={revision} editable={current && item.status === 'READY' && revision.status === 'READY'} previewError={current ? 'Open the current ready draft page to load its protected HTML preview.' : 'Historical HTML artifacts are not exposed by the current-revision preview endpoint.'} onRevisionChange={(updated) => setRevision(updated)} />
  </Stack>
}
