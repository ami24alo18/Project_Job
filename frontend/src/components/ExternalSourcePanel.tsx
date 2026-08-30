import { Alert, Button, Checkbox, FormControlLabel, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { apiErrorMessage } from '../api/apiErrors'
import { jobSourcesApi } from '../api/jobSourcesApi'
import type { DatePostedWindow, JobSourceConfiguration, JobSourceSearchRule, JobSourceSearchRuleInput, WebhookTokenRotation } from '../types/jobs'

type RuleForm = Omit<JobSourceSearchRuleInput, 'locations'> & { locations: string }

const defaultRule: RuleForm = {
  name: '', query: '', locations: '', remoteAllowed: true, hybridAllowed: true, onsiteAllowed: true,
  datePostedWindow: 'TODAY', maximumResults: 100, enabled: true,
}

export function ExternalSourcePanel({ source }: { source: JobSourceConfiguration }) {
  const [rules, setRules] = useState<JobSourceSearchRule[]>([])
  const [form, setForm] = useState<RuleForm>(defaultRule)
  const [issued, setIssued] = useState<WebhookTokenRotation | null>(null)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const loadRules = useCallback(() => jobSourcesApi.searchRules(source.id)
    .then(items => { setRules(items); setError('') })
    .catch(problem => setError(apiErrorMessage(problem, 'Search rules could not be loaded'))), [source.id])
  useEffect(() => { void loadRules() }, [loadRules])
  const set = <K extends keyof RuleForm>(key: K, value: RuleForm[K]) => setForm(current => ({ ...current, [key]: value }))
  const createRule = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setMessage('')
    if (!form.name.trim() || !form.query.trim()) { setError('Rule name and query are required'); return }
    if (!form.remoteAllowed && !form.hybridAllowed && !form.onsiteAllowed) { setError('Select at least one workplace type'); return }
    if (!Number.isInteger(form.maximumResults) || form.maximumResults < 1 || form.maximumResults > 1000) { setError('Maximum results must be between 1 and 1000'); return }
    try {
      setBusy('rule')
      await jobSourcesApi.createSearchRule(source.id, {
        ...form,
        name: form.name.trim(),
        query: form.query.trim(),
        locations: form.locations.split(',').map(value => value.trim()).filter(Boolean),
      })
      setForm(defaultRule); setMessage('Search rule created'); await loadRules()
    } catch (problem) { setError(apiErrorMessage(problem, 'Search rule could not be created')) } finally { setBusy('') }
  }
  const toggleRule = async (rule: JobSourceSearchRule) => {
    setBusy(rule.id); setError(''); setMessage('')
    try { await jobSourcesApi.setSearchRuleEnabled(source.id, rule.id, !rule.enabled); await loadRules() }
    catch (problem) { setError(apiErrorMessage(problem, 'Search rule could not be changed')) } finally { setBusy('') }
  }
  const rotate = async () => {
    if (!window.confirm('Rotate this token? The current n8n credential will stop working immediately.')) return
    setBusy('rotate'); setError(''); setMessage('')
    try { const result = await jobSourcesApi.rotateToken(source.id); setIssued(result); setMessage('Webhook token rotated') }
    catch (problem) { setError(apiErrorMessage(problem, 'Webhook token could not be rotated')) } finally { setBusy('') }
  }
  const copy = async (value: string) => {
    try { await navigator.clipboard.writeText(value); setMessage('Copied to clipboard') }
    catch { setError('Clipboard access was unavailable; select and copy the value manually') }
  }
  return <Stack spacing={3}>
    {error && <Alert severity="error">{error}</Alert>}{message && <Alert severity="success">{message}</Alert>}
    <Paper variant="outlined" sx={{ p: 3 }}><Stack spacing={2}>
      <Typography variant="h6">n8n webhook</Typography>
      <Typography variant="body2">Endpoint: <code>/api/v1/job-sources/{source.id}/external-events</code></Typography>
      <Typography variant="body2" color="text.secondary">The token is stored only as a digest. Rotate it if the original one-time value was lost.</Typography>
      <Button variant="outlined" disabled={!!source.archivedAt || busy === 'rotate'} onClick={() => void rotate()} sx={{ alignSelf: 'flex-start' }}>Rotate webhook token</Button>
      {issued && <Alert severity="warning"><Stack spacing={1}>
        <strong>Copy this token now. It will not be shown again.</strong>
        <TextField label="Webhook token (shown once)" value={issued.webhookToken} slotProps={{ input: { readOnly: true } }} />
        <Stack direction="row" spacing={1}><Button onClick={() => void copy(issued.webhookToken)}>Copy token</Button><Button onClick={() => void copy(issued.webhookUrl)}>Copy endpoint</Button></Stack>
      </Stack></Alert>}
    </Stack></Paper>
    <Paper component="form" onSubmit={createRule} variant="outlined" sx={{ p: 3 }}><Stack spacing={2}>
      <Typography variant="h6">Add search rule</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}><TextField fullWidth required label="Rule name" value={form.name} onChange={event => set('name', event.target.value)} /><TextField fullWidth required label="Search query" value={form.query} onChange={event => set('query', event.target.value)} /></Stack>
      <TextField fullWidth label="Locations (comma separated)" value={form.locations} onChange={event => set('locations', event.target.value)} />
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <TextField select label="Date posted" value={form.datePostedWindow} onChange={event => set('datePostedWindow', event.target.value as DatePostedWindow)} sx={{ minWidth: 180 }}>{(['ANY', 'TODAY', 'THREE_DAYS', 'WEEK', 'MONTH'] as const).map(value => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>
        <TextField type="number" label="Maximum results" value={form.maximumResults} onChange={event => set('maximumResults', Number(event.target.value))} slotProps={{ htmlInput: { min: 1, max: 1000 } }} />
      </Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }}><FormControlLabel control={<Checkbox checked={form.remoteAllowed} onChange={event => set('remoteAllowed', event.target.checked)} />} label="Remote" /><FormControlLabel control={<Checkbox checked={form.hybridAllowed} onChange={event => set('hybridAllowed', event.target.checked)} />} label="Hybrid" /><FormControlLabel control={<Checkbox checked={form.onsiteAllowed} onChange={event => set('onsiteAllowed', event.target.checked)} />} label="Onsite" /></Stack>
      <Button type="submit" variant="contained" disabled={busy === 'rule' || !!source.archivedAt} sx={{ alignSelf: 'flex-start' }}>Add search rule</Button>
    </Stack></Paper>
    <Paper variant="outlined" sx={{ p: 3 }}><Typography variant="h6" gutterBottom>Search rules</Typography>{rules.length === 0 ? <Typography color="text.secondary">No search rules configured.</Typography> : <Stack spacing={2}>{rules.map(rule => <Stack key={rule.id} direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}><div><Typography fontWeight={600}>{rule.name}</Typography><Typography variant="body2">{rule.query}{rule.locations.length ? ` · ${rule.locations.join(', ')}` : ''}</Typography><Typography variant="caption">{rule.datePostedWindow} · max {rule.maximumResults} · {rule.remoteAllowed ? 'remote ' : ''}{rule.hybridAllowed ? 'hybrid ' : ''}{rule.onsiteAllowed ? 'onsite' : ''}</Typography></div><Button disabled={busy === rule.id || !!source.archivedAt} onClick={() => void toggleRule(rule)}>{rule.enabled ? 'Disable rule' : 'Enable rule'}</Button></Stack>)}</Stack>}</Paper>
  </Stack>
}
