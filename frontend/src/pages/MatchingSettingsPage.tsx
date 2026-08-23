import { Alert, Button, Grid, Paper, Stack, TextField, Typography } from '@mui/material'
import { useEffect, useMemo, useState } from 'react'
import { apiErrorMessage } from '../api/apiErrors'
import { matchingApi, type MatchingConfiguration, type MatchingConfigurationUpdate } from '../api/matchingApi'

function updateRequest(configuration: MatchingConfiguration): MatchingConfigurationUpdate {
  return {
    skillsWeight: configuration.skillsWeight,
    experienceWeight: configuration.experienceWeight,
    roleWeight: configuration.roleWeight,
    locationWeight: configuration.locationWeight,
    domainWeight: configuration.domainWeight,
    compensationWeight: configuration.compensationWeight,
    strongApplyThreshold: configuration.strongApplyThreshold,
    applyThreshold: configuration.applyThreshold,
    manualReviewThreshold: configuration.manualReviewThreshold,
    maximumAllowedExperienceGap: configuration.maximumAllowedExperienceGap,
    maximumJobsPerBatch: configuration.maximumJobsPerBatch,
    maximumDailyAiRequests: configuration.maximumDailyAiRequests,
    maximumDailyInputTokens: configuration.maximumDailyInputTokens,
    rulesetVersion: configuration.rulesetVersion,
    recordVersion: configuration.recordVersion,
  }
}

export function MatchingSettingsPage() {
  const [configuration, setConfiguration] = useState<MatchingConfiguration>()
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    matchingApi.get().then(setConfiguration).catch(problem =>
      setError(apiErrorMessage(problem, 'Could not load matching settings')))
  }, [])

  const total = useMemo(() => configuration
    ? configuration.skillsWeight + configuration.experienceWeight + configuration.roleWeight
      + configuration.locationWeight + configuration.domainWeight + configuration.compensationWeight
    : 0, [configuration])

  if (!configuration) {
    return error ? <Alert severity="error">{error}</Alert> : <Typography>Loading matching settings…</Typography>
  }

  const number = (key: keyof MatchingConfiguration) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setConfiguration({ ...configuration, [key]: Number(event.target.value) })
    setSaved(false)
  }
  const invalid = total !== 100 || !(configuration.strongApplyThreshold > configuration.applyThreshold
    && configuration.applyThreshold > configuration.manualReviewThreshold)
  const save = async () => {
    setError('')
    setSaved(false)
    try {
      setConfiguration(await matchingApi.put(updateRequest(configuration)))
      setSaved(true)
    } catch (problem) {
      setError(apiErrorMessage(problem, 'Could not save matching settings'))
    }
  }

  return <Stack spacing={3}>
    <div><Typography variant="h4">Matching settings</Typography><Typography color="text.secondary">Server-side weights, recommendation thresholds, and AI budgets. The API key is configured only on the backend.</Typography></div>
    {error && <Alert severity="error">{error}</Alert>}
    {saved && <Alert severity="success">Settings saved. Existing evaluations may now be stale.</Alert>}
    <Paper sx={{ p: 3 }}><Grid container spacing={2}>
      {(['skills', 'experience', 'role', 'location', 'domain', 'compensation'] as const).map(name =>
        <Grid size={{ xs: 12, sm: 6, md: 4 }} key={name}><TextField fullWidth type="number" label={`${name} weight`} value={configuration[`${name}Weight`]} onChange={number(`${name}Weight`)} /></Grid>)}
      <Grid size={12}><Alert severity={total === 100 ? 'success' : 'error'}>Weights total: {total} (must equal 100)</Alert></Grid>
      {([['strongApplyThreshold', 'Strong apply threshold'], ['applyThreshold', 'Apply threshold'], ['manualReviewThreshold', 'Manual review threshold'], ['maximumJobsPerBatch', 'Maximum jobs per batch'], ['maximumDailyAiRequests', 'Maximum daily AI requests'], ['maximumDailyInputTokens', 'Maximum daily input tokens']] as const).map(([key, label]) =>
        <Grid size={{ xs: 12, sm: 6, md: 4 }} key={key}><TextField fullWidth type="number" label={label} value={configuration[key]} onChange={number(key)} /></Grid>)}
      <Grid size={12}><Button variant="contained" disabled={invalid} onClick={() => void save()}>Save settings</Button></Grid>
    </Grid></Paper>
  </Stack>
}
