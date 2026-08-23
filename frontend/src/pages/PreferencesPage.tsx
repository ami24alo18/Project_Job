import axios from 'axios'
import { Alert, Button, Checkbox, FormControlLabel, Grid, Paper, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useState, type FormEvent } from 'react'
import { apiErrorMessage, isConflict } from '../api/apiErrors'
import { profileApi } from '../api/profileApi'
import type { SearchPreference } from '../types/profile'

type Form = Omit<SearchPreference, 'profileId'>
type ListKey = 'targetTitles' | 'preferredLocations' | 'excludedLocations' | 'requiredSkills' | 'preferredSkills' | 'excludedCompanies' | 'excludedKeywords'
type ListInputs = Record<ListKey, string>

const empty: Form = {
  targetTitles: [], preferredLocations: [], excludedLocations: [], requiredSkills: [], preferredSkills: [],
  excludedCompanies: [], excludedKeywords: [], minimumExperienceYears: 0, maximumExperienceYears: 10,
  minimumMatchScore: 60, maximumDailyShortlist: 20, maximumDailyApplications: 5,
  remoteAllowed: true, hybridAllowed: true, onsiteAllowed: true, recordVersion: 0,
}
const listFields: [ListKey, string][] = [
  ['targetTitles', 'Target titles'], ['preferredLocations', 'Preferred locations'],
  ['excludedLocations', 'Excluded locations'], ['requiredSkills', 'Required skills'],
  ['preferredSkills', 'Preferred skills'], ['excludedCompanies', 'Excluded companies'],
  ['excludedKeywords', 'Excluded keywords'],
]
const numberFields: [keyof Form, string][] = [
  ['minimumExperienceYears', 'Minimum experience years'], ['maximumExperienceYears', 'Maximum experience years'],
  ['minimumMatchScore', 'Minimum match score'], ['maximumDailyShortlist', 'Maximum daily shortlist'],
  ['maximumDailyApplications', 'Maximum daily applications'],
]
const emptyListInputs = (): ListInputs => Object.fromEntries(listFields.map(([key]) => [key, ''])) as ListInputs
const listInputsFrom = (value: Form): ListInputs => Object.fromEntries(listFields.map(([key]) => [key, value[key].join(', ')])) as ListInputs
const csv = (input: string) => {
  const unique = new Map<string, string>()
  for (const raw of input.split(',')) {
    const value = raw.trim()
    if (value && !unique.has(value.toLowerCase())) unique.set(value.toLowerCase(), value)
  }
  return [...unique.values()]
}
const overlaps = (left: string[], right: string[]) => {
  const values = new Set(left.map(value => value.toLowerCase()))
  return right.some(value => values.has(value.toLowerCase()))
}

export function PreferencesPage() {
  const [form, setForm] = useState<Form>(empty)
  const [listInputs, setListInputs] = useState<ListInputs>(emptyListInputs)
  const [exists, setExists] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    profileApi.getPreferences().then(preferences => {
      setForm(preferences)
      setListInputs(listInputsFrom(preferences))
      setExists(true)
    }, requestError => {
      if (!axios.isAxiosError(requestError) || requestError.response?.status !== 404) setError('Could not load preferences')
    })
  }, [])

  const set = <K extends keyof Form,>(key: K, value: Form[K]) => {
    setForm(current => ({ ...current, [key]: value }))
    setMessage('')
  }
  const setListInput = (key: ListKey, value: string) => {
    setListInputs(current => ({ ...current, [key]: value }))
    setMessage('')
  }
  const save = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    setMessage('')
    try {
      const lists = Object.fromEntries(listFields.map(([key]) => [key, csv(listInputs[key])])) as Pick<Form, ListKey>
      if (lists.targetTitles.length === 0) {
        setError('At least one target title is required')
        return
      }
      if (lists.preferredLocations.length === 0 && !form.remoteAllowed) {
        setError('At least one preferred location or remote work is required')
        return
      }
      if (form.minimumExperienceYears > form.maximumExperienceYears) {
        setError('Maximum experience must be greater than or equal to minimum experience')
        return
      }
      if (overlaps(lists.requiredSkills, lists.preferredSkills)) {
        setError('A skill cannot be both required and preferred')
        return
      }
      if (overlaps(lists.preferredLocations, lists.excludedLocations)) {
        setError('A location cannot be both preferred and excluded')
        return
      }
      const body = {
        ...lists,
        minimumExperienceYears: form.minimumExperienceYears,
        maximumExperienceYears: form.maximumExperienceYears,
        minimumMatchScore: form.minimumMatchScore,
        maximumDailyShortlist: form.maximumDailyShortlist,
        maximumDailyApplications: form.maximumDailyApplications,
        remoteAllowed: form.remoteAllowed,
        hybridAllowed: form.hybridAllowed,
        onsiteAllowed: form.onsiteAllowed,
        recordVersion: exists ? form.recordVersion : undefined,
      }
      const saved = await profileApi.savePreferences(body)
      setForm(saved)
      setListInputs(listInputsFrom(saved))
      setExists(true)
      setMessage('Preferences saved')
    } catch (requestError) {
      setError(isConflict(requestError)
        ? 'Preferences changed elsewhere. Reload the page and try again.'
        : apiErrorMessage(requestError, 'Preferences could not be saved'))
    }
  }

  return <Stack spacing={3}>
    <Typography variant="h4">Search preferences</Typography>
    <Typography color="text.secondary">Enter list values separated by commas; spaces within a value are preserved. Values are trimmed and deduplicated when saved.</Typography>
    {error && <Alert severity="error">{error}</Alert>}
    {message && <Alert severity="success">{message}</Alert>}
    <Paper component="form" onSubmit={save} sx={{ p: 3 }}>
      <Grid container spacing={2}>
        {listFields.map(([key, label]) => <Grid size={{ xs: 12, md: 6 }} key={key}>
          <TextField fullWidth label={label} value={listInputs[key]} onChange={event => setListInput(key, event.target.value)} />
        </Grid>)}
        {numberFields.map(([key, label]) => <Grid size={{ xs: 12, sm: 6, md: 4 }} key={key}>
          <TextField fullWidth type="number" label={label} value={String(form[key])} onChange={event => set(key, Number(event.target.value) as Form[typeof key])} />
        </Grid>)}
        <Grid size={12}>{(['remoteAllowed', 'hybridAllowed', 'onsiteAllowed'] as const).map(key =>
          <FormControlLabel key={key} control={<Checkbox checked={form[key]} onChange={event => set(key, event.target.checked)} />} label={key.replace('Allowed', '')} />
        )}</Grid>
        <Grid size={12}><Button variant="contained" type="submit">Save preferences</Button></Grid>
      </Grid>
    </Paper>
  </Stack>
}
