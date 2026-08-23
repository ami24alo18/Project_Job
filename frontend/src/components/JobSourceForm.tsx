import { Checkbox, FormControlLabel, Grid, MenuItem, TextField } from '@mui/material'
import type { JobSourceInput, SourceRegion } from '../types/jobs'
import { regionsFor } from '../utils/jobSourceForms'

export function JobSourceForm({ value, onChange, includeEnabled = false, disabled = false }: {
  value: JobSourceInput
  onChange: (value: JobSourceInput) => void
  includeEnabled?: boolean
  disabled?: boolean
}) {
  const set = <K extends keyof JobSourceInput>(key: K, next: JobSourceInput[K]) => onChange({ ...value, [key]: next })
  const changeType = (sourceType: JobSourceInput['sourceType']) => onChange({ ...value, sourceType, region: regionsFor(sourceType)[0] })
  const identifierLabel = value.sourceType === 'LEVER' ? 'Lever site identifier' : value.sourceType === 'GREENHOUSE' ? 'Greenhouse board token' : 'Email alert source name'
  return <Grid container spacing={2}>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required disabled={disabled} label="Display name" value={value.displayName} onChange={event => set('displayName', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, md: 3 }}><TextField select fullWidth disabled={disabled} label="Source type" value={value.sourceType} onChange={event => changeType(event.target.value as JobSourceInput['sourceType'])}>
      <MenuItem value="LEVER">Lever</MenuItem><MenuItem value="GREENHOUSE">Greenhouse</MenuItem><MenuItem value="EMAIL_WEBHOOK">Email webhook</MenuItem>
    </TextField></Grid>
    <Grid size={{ xs: 12, md: 3 }}><TextField select fullWidth disabled={disabled} label="Region" value={value.region} onChange={event => set('region', event.target.value as SourceRegion)}>
      {regionsFor(value.sourceType).map(region => <MenuItem key={region} value={region}>{region}</MenuItem>)}
    </TextField></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required disabled={disabled} label={identifierLabel} value={value.providerIdentifier} onChange={event => set('providerIdentifier', event.target.value)} helperText="Only an official provider identifier is accepted; arbitrary URLs are not configurable." /></Grid>
    <Grid size={{ xs: 12, sm: 4, md: 2 }}><TextField fullWidth disabled={disabled || value.sourceType === 'EMAIL_WEBHOOK'} type="number" label="Page size" value={value.pageSize} onChange={event => set('pageSize', Number(event.target.value))} /></Grid>
    <Grid size={{ xs: 12, sm: 4, md: 2 }}><TextField fullWidth disabled={disabled || value.sourceType === 'EMAIL_WEBHOOK'} type="number" label="Maximum pages" value={value.maximumPagesPerRun} onChange={event => set('maximumPagesPerRun', Number(event.target.value))} /></Grid>
    <Grid size={{ xs: 12, sm: 4, md: 2 }}><TextField fullWidth disabled={disabled} type="number" label="Missing-run threshold" value={value.missingRunThreshold} onChange={event => set('missingRunThreshold', Number(event.target.value))} /></Grid>
    {includeEnabled && <Grid size={12}><FormControlLabel control={<Checkbox disabled={disabled} checked={value.enabled} onChange={event => set('enabled', event.target.checked)} />} label="Enabled after creation" /></Grid>}
  </Grid>
}
