import { Alert, Button, Paper, Stack, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
import { apiErrorMessage } from '../api/apiErrors'
import { jobsApi } from '../api/jobsApi'
import { JobFields } from '../components/JobFields'
import { emptyJobForm, jobFormToInput, validateJobForm, type JobFormState } from '../utils/jobForms'

function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() ?? `manual-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function NewJobPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<JobFormState>(emptyJobForm)
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const save = async (event: FormEvent) => {
    event.preventDefault(); setError('')
    const validation = validateJobForm(form)
    if (validation) { setError(validation); return }
    try {
      setSaving(true)
      const created = await jobsApi.createManual(jobFormToInput(form), idempotencyKey)
      setIdempotencyKey(newIdempotencyKey())
      navigate(`/jobs/${created.id}`)
    } catch (problem) { setError(apiErrorMessage(problem, 'Manual job could not be created')) } finally { setSaving(false) }
  }
  return <Stack spacing={3}>
    <Stack direction="row" justifyContent="space-between"><div><Typography variant="h4">Add a manual job</Typography><Typography color="text.secondary">Manual URLs are validated but never fetched by the backend.</Typography></div><Button component={RouterLink} to="/jobs">Cancel</Button></Stack>
    {error && <Alert severity="error">{error}</Alert>}
    <Paper component="form" noValidate onSubmit={save} sx={{ p: 3 }}><Stack spacing={2}><JobFields value={form} onChange={setForm} /><Alert severity="info">A private idempotency key is reused if this submission must be retried. It is replaced only after success.</Alert><Button type="submit" variant="contained" disabled={saving} sx={{ alignSelf: 'flex-start' }}>{saving ? 'Saving…' : 'Create manual job'}</Button></Stack></Paper>
  </Stack>
}
