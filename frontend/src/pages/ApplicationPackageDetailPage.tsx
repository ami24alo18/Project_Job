import { Alert, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, List, ListItem, ListItemButton, ListItemText, Paper, Stack, TextField, Typography } from '@mui/material'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { applicationPackagesApi, createApplicationPackageIdempotencyKey } from '../api/applicationPackagesApi'
import { apiErrorMessage } from '../api/apiErrors'
import { ApplicationDraftBanner } from '../components/ApplicationDraftBanner'
import { ApplicationRevisionInspector } from '../components/ApplicationRevisionInspector'
import type { ApplicationPackageDetail, ApplicationPackageRevision } from '../types/applicationPackages'

export function ApplicationPackageDetailPage() {
  const { packageId = '' } = useParams()
  const [item, setItem] = useState<ApplicationPackageDetail | null>(null)
  const [revisions, setRevisions] = useState<ApplicationPackageRevision[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [previewHtml, setPreviewHtml] = useState<string>()
  const [previewError, setPreviewError] = useState('')
  const [dialogOpen, setDialogOpen] = useState(false)
  const [replaceUserEdited, setReplaceUserEdited] = useState(false)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const regenerationKey = useRef<string | null>(null)
  const load = useCallback(() => applicationPackagesApi.get(packageId).then(detail => {
    setItem(detail); setPreviewHtml(undefined); setPreviewError('')
    return applicationPackagesApi.revisions(packageId).then(setRevisions, problem => setError(apiErrorMessage(problem, 'Revision history could not be loaded')))
  }, problem => { setError(apiErrorMessage(problem, 'Application package could not be loaded')); setItem(null) }).finally(() => setLoading(false)), [packageId])
  useEffect(() => { void load() }, [load])
  const revision = item?.currentRevision
  const revisionId = revision?.id
  const revisionStatus = revision?.status
  useEffect(() => {
    let active = true
    if (!revisionId || revisionStatus !== 'READY') return () => { active = false }
    applicationPackagesApi.preview(packageId).then(html => { if (active) setPreviewHtml(html) }, problem => { if (active) setPreviewError(apiErrorMessage(problem, 'The protected HTML preview could not be loaded')) })
    return () => { active = false }
  }, [packageId, revisionId, revisionStatus])
  const editedCount = useMemo(() => revision?.contents.filter(content => content.userEdited || content.origin === 'USER_EDITED').length ?? 0, [revision])
  const updateRevision = (updated: ApplicationPackageRevision, change?: 'CONTENT_EDIT') => {
    if (change === 'CONTENT_EDIT') {
      setPreviewHtml(undefined)
      setPreviewError('The previous preview and downloads are unavailable after an edit. Regenerate explicitly to create validated artifacts.')
      setItem(current => current ? {
        ...current,
        status: 'STALE',
        stale: true,
        staleReason: 'USER_EDIT_REQUIRES_REVALIDATION_AND_REGENERATION',
        artifactTypes: [],
        currentRevision: updated,
      } : current)
      return
    }
    setItem(current => current ? { ...current, currentRevision: updated } : current)
  }
  const regenerate = async () => {
    setBusy(true); setError(''); setMessage('')
    try {
      regenerationKey.current ??= createApplicationPackageIdempotencyKey()
      const updated = await applicationPackagesApi.regenerate(packageId, { replaceUserEdited, reason: reason.trim() || undefined }, regenerationKey.current)
      setItem(updated); setDialogOpen(false); setReplaceUserEdited(false); setReason(''); setMessage('A new application-package revision was requested. Historical content remains available.')
      setPreviewHtml(undefined); setPreviewError(''); regenerationKey.current = null
      try { setRevisions(await applicationPackagesApi.revisions(packageId)) } catch { /* detail remains usable */ }
    } catch (problem) { setError(apiErrorMessage(problem, 'Regeneration could not be requested')) }
    finally { setBusy(false) }
  }
  const validate = async () => {
    setBusy(true); setError(''); setMessage('')
    try { const result = await applicationPackagesApi.validate(packageId); if (result.valid) setMessage('Validation passed. All factual claims remain traceable to eligible sources.'); else setError(`Validation failed: ${result.validationCodes.join(', ')}`) }
    catch (problem) { setError(apiErrorMessage(problem, 'Validation could not be completed')) }
    finally { setBusy(false) }
  }
  const archive = async () => {
    setBusy(true); setError(''); setMessage('')
    try { setItem(await applicationPackagesApi.archive(packageId)); setMessage('Application-package draft archived. No external action was taken.') }
    catch (problem) { setError(apiErrorMessage(problem, 'Application package could not be archived')) }
    finally { setBusy(false) }
  }
  if (loading) return <Typography>Loading application-package draft…</Typography>
  if (!item) return <Alert severity="error" action={<Button onClick={() => { setLoading(true); setError(''); void load() }}>Retry</Button>}>{error || 'Application package was not found'}</Alert>
  return <Stack spacing={3}>
    <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}><div><Typography variant="h4">{item.jobTitle}</Typography><Typography variant="h6" color="text.secondary">{item.company}</Typography></div><Button component={RouterLink} to="/application-packages">Back to packages</Button></Stack>
    <ApplicationDraftBanner />
    {error && <Alert severity="error">{error}</Alert>}{message && <Alert severity="success">{message}</Alert>}
    <Paper variant="outlined" sx={{ p: 2 }}><Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}><Stack spacing={1}><Stack direction="row" spacing={1} flexWrap="wrap"><Chip label={item.status} color={item.status === 'READY' ? 'success' : item.status === 'FAILED' ? 'error' : item.status === 'STALE' ? 'warning' : 'info'} /><Chip variant="outlined" label={`Revision ${item.currentRevisionNumber ?? 'pending'}`} />{item.stale && <Chip color="warning" label="STALE SOURCES" />}</Stack><Typography variant="body2">Created by {item.createdBy} on {new Date(item.createdAt).toLocaleString()}</Typography></Stack><Stack direction="row" spacing={1} flexWrap="wrap"><Button disabled={busy || !revision} onClick={() => void validate()}>Validate claims</Button><Button variant="contained" disabled={busy || item.status === 'ARCHIVED'} onClick={() => setDialogOpen(true)}>Regenerate draft</Button>{item.status !== 'ARCHIVED' && <Button color="warning" disabled={busy} onClick={() => void archive()}>Archive</Button>}<Button disabled={busy} onClick={() => void load()}>Refresh</Button></Stack></Stack></Paper>
    {item.stale && <Alert severity="warning"><Typography fontWeight={600}>This draft uses stale sources.</Typography>{item.staleReason || 'The job, evaluation, profile, or generation policy changed. Regenerate explicitly; this historical revision will not be rewritten.'}</Alert>}
    {(item.status === 'REQUESTED' || item.status === 'GENERATING') && <Alert severity="info">Generation is in progress. No application is being submitted. Refresh to check for the completed draft.</Alert>}
    {item.status === 'FAILED' && <Alert severity="error">Generation failed closed. {revision?.failureCode && `${revision.failureCode}: `}{revision?.failureMessage || 'No unvalidated content or document was published.'}</Alert>}
    {revision ? <ApplicationRevisionInspector packageId={packageId} revision={revision} editable={item.status === 'READY' && revision.status === 'READY'} previewHtml={previewHtml} previewError={previewError || (revision.status === 'READY' ? undefined : 'The protected HTML preview becomes available when this revision is ready and up to date.')} onRevisionChange={updateRevision} /> : <Alert severity="info">The first immutable revision has not been created yet.</Alert>}
    <Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h5">Revision history</Typography>{revisions.length === 0 ? <Typography color="text.secondary" sx={{ mt: 1 }}>No historical revisions are available.</Typography> : <List>{[...revisions].sort((a, b) => b.revisionNumber - a.revisionNumber).map(history => <ListItem key={history.id} disablePadding secondaryAction={<Chip size="small" label={history.status} />}><ListItemButton component={RouterLink} to={`/application-packages/${packageId}/revisions/${history.id}`}><ListItemText primary={`Revision ${history.revisionNumber}`} secondary={new Date(history.createdAt).toLocaleString()} /></ListItemButton></ListItem>)}</List>}</Paper>
    <Dialog open={dialogOpen} onClose={() => { if (!busy) { setDialogOpen(false); regenerationKey.current = null } }} fullWidth maxWidth="sm">
      <DialogTitle>Generate a new draft revision?</DialogTitle>
      <DialogContent><Stack spacing={2} sx={{ pt: 1 }}><Alert severity="info">Regeneration creates a new immutable revision. It is not approval and does not submit an application.</Alert>{editedCount > 0 ? <Alert severity="warning">This draft contains {editedCount} user-edited {editedCount === 1 ? 'section' : 'sections'}. The backend will not regenerate until you explicitly confirm replacement.</Alert> : <Typography>No user-edited sections would be replaced.</Typography>}<TextField fullWidth label="Regeneration reason (optional)" value={reason} onChange={event => { setReason(event.target.value); regenerationKey.current = null }} inputProps={{ maxLength: 500 }} />{editedCount > 0 && <FormControlLabel control={<Checkbox checked={replaceUserEdited} onChange={event => { setReplaceUserEdited(event.target.checked); regenerationKey.current = null }} />} label="I understand that regeneration will replace my edited content" />}</Stack></DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => { setDialogOpen(false); regenerationKey.current = null }}>Cancel</Button><Button variant="contained" color={replaceUserEdited ? 'warning' : 'primary'} disabled={busy || (editedCount > 0 && !replaceUserEdited)} onClick={() => void regenerate()}>{editedCount > 0 ? (replaceUserEdited ? 'Replace edits and regenerate' : 'Confirm replacement to regenerate') : 'Regenerate draft'}</Button></DialogActions>
    </Dialog>
  </Stack>
}
