import { Alert, Button, Checkbox, Chip, FormControlLabel, Paper, Stack, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { apiErrorMessage } from '../api/apiErrors'
import { jobSourcesApi } from '../api/jobSourcesApi'
import type { CareerSiteDiscovery } from '../types/jobs'

export function CareerSiteOnboarding({ onCreated }: { onCreated: () => Promise<void> }) {
  const [companyName, setCompanyName] = useState('')
  const [careerSiteUrl, setCareerSiteUrl] = useState('')
  const [enableWhenSupported, setEnableWhenSupported] = useState(true)
  const [detection, setDetection] = useState<CareerSiteDiscovery | null>(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const discover = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setMessage(''); setDetection(null)
    const company = companyName.trim(); const url = careerSiteUrl.trim()
    if (!company || !url) { setError('Company name and career-site URL are required'); return }
    try {
      const parsed = new URL(url)
      if (parsed.protocol !== 'https:') { setError('Career-site discovery accepts HTTPS URLs only'); return }
    } catch { setError('Enter a valid HTTPS career-site URL'); return }
    try {
      setBusy('discover')
      setDetection(await jobSourcesApi.discoverCareerSite({ companyName: company, careerSiteUrl: url }))
    } catch (problem) { setError(apiErrorMessage(problem, 'The career site could not be inspected safely')) }
    finally { setBusy('') }
  }

  const create = async () => {
    if (!detection) return
    try {
      setBusy('create'); setError(''); setMessage('')
      const created = await jobSourcesApi.createCareerSite({
        companyName: companyName.trim(), careerSiteUrl: careerSiteUrl.trim(),
        enabled: detection.supportStatus === 'SUPPORTED' && enableWhenSupported,
        pageSize: 50, maximumPagesPerRun: 20, missingRunThreshold: 2,
      })
      setMessage(created.enabled ? `${created.displayName} was created and enabled` : `${created.displayName} was saved disabled for follow-up`)
      setCompanyName(''); setCareerSiteUrl(''); setDetection(null); await onCreated()
    } catch (problem) { setError(apiErrorMessage(problem, 'The career-site source could not be created')) }
    finally { setBusy('') }
  }

  return <Paper component="form" onSubmit={discover} sx={{ p: 3 }}><Stack spacing={2}>
    <div><Typography variant="h6">Add company career site</Typography><Typography variant="body2" color="text.secondary">Inspect an employer URL, detect a reviewed connector, then save it. The server repeats discovery during creation.</Typography></div>
    {error && <Alert severity="error">{error}</Alert>}{message && <Alert severity="success">{message}</Alert>}
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
      <TextField required fullWidth label="Company name" value={companyName} onChange={event => { setCompanyName(event.target.value); setDetection(null) }} />
      <TextField required fullWidth label="Career-site URL" placeholder="https://careers.example.com/jobs" value={careerSiteUrl} onChange={event => { setCareerSiteUrl(event.target.value); setDetection(null) }} />
    </Stack>
    <Button type="submit" variant="outlined" disabled={!!busy} sx={{ alignSelf: 'flex-start' }}>Inspect career site</Button>
    {detection && <Alert severity={detection.supportStatus === 'SUPPORTED' ? 'success' : detection.supportStatus === 'UNSUPPORTED' ? 'error' : 'warning'}>
      <Stack spacing={1}>
        <Stack direction="row" spacing={1} flexWrap="wrap"><Chip size="small" label={detection.supportStatus} />{detection.connectorType && <Chip size="small" variant="outlined" label={detection.connectorType} />}</Stack>
        <Typography variant="body2">{detection.supportMessage}</Typography>
        <Typography variant="caption">Canonical host: {detection.canonicalHost}</Typography>
        {detection.supportStatus === 'SUPPORTED' && <FormControlLabel control={<Checkbox checked={enableWhenSupported} onChange={event => setEnableWhenSupported(event.target.checked)} />} label="Enable after creation" />}
        <Button variant="contained" disabled={!!busy} onClick={() => void create()} sx={{ alignSelf: 'flex-start' }}>{detection.supportStatus === 'SUPPORTED' ? 'Create career source' : 'Save disabled source'}</Button>
      </Stack>
    </Alert>}
  </Stack></Paper>
}
