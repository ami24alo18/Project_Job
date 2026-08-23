import { Grid, MenuItem, TextField } from '@mui/material'
import type { EmploymentType, SalaryInterval, WorkplaceType } from '../types/jobs'
import type { JobFormState } from '../utils/jobForms'
const workplaces: WorkplaceType[] = ['REMOTE', 'HYBRID', 'ONSITE', 'UNSPECIFIED']
const employments: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'CONTRACT', 'TEMPORARY', 'INTERNSHIP', 'OTHER', 'UNSPECIFIED']
const salaryIntervals: SalaryInterval[] = ['HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR', 'OTHER', 'UNSPECIFIED']

export function JobFields({ value, onChange, disabled = false }: { value: JobFormState; onChange: (value: JobFormState) => void; disabled?: boolean }) {
  const set = <K extends keyof JobFormState>(key: K, next: JobFormState[K]) => onChange({ ...value, [key]: next })
  return <Grid container spacing={2}>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required disabled={disabled} label="Company" value={value.company} onChange={event => set('company', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth required disabled={disabled} label="Job title" value={value.title} onChange={event => set('title', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth disabled={disabled} label="Location" value={value.location} onChange={event => set('location', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, md: 2 }}><TextField fullWidth disabled={disabled} label="Country code" value={value.countryCode} onChange={event => set('countryCode', event.target.value)} inputProps={{ maxLength: 2 }} /></Grid>
    <Grid size={{ xs: 12, md: 2 }}><TextField select fullWidth disabled={disabled} label="Workplace type" value={value.workplaceType} onChange={event => set('workplaceType', event.target.value as WorkplaceType)}>{workplaces.map(item => <MenuItem key={item} value={item}>{item}</MenuItem>)}</TextField></Grid>
    <Grid size={{ xs: 12, md: 2 }}><TextField select fullWidth disabled={disabled} label="Employment type" value={value.employmentType} onChange={event => set('employmentType', event.target.value as EmploymentType)}>{employments.map(item => <MenuItem key={item} value={item}>{item}</MenuItem>)}</TextField></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth disabled={disabled} label="Department" value={value.department} onChange={event => set('department', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth disabled={disabled} label="Team" value={value.team} onChange={event => set('team', event.target.value)} /></Grid>
    <Grid size={12}><TextField fullWidth multiline minRows={6} disabled={disabled} label="Description" value={value.description} onChange={event => set('description', event.target.value)} helperText="Descriptions are handled and displayed only as untrusted plain text." /></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth disabled={disabled} label="Application URL" value={value.applyUrl} onChange={event => set('applyUrl', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth disabled={disabled} label="Source URL" value={value.sourceUrl} onChange={event => set('sourceUrl', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth disabled={disabled} type="number" label="Salary minimum" value={value.salaryMinimum} onChange={event => set('salaryMinimum', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth disabled={disabled} type="number" label="Salary maximum" value={value.salaryMaximum} onChange={event => set('salaryMaximum', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth disabled={disabled} label="Salary currency" value={value.salaryCurrency} onChange={event => set('salaryCurrency', event.target.value)} /></Grid>
    <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField select fullWidth disabled={disabled} label="Salary interval" value={value.salaryInterval} onChange={event => set('salaryInterval', event.target.value as SalaryInterval | '')}><MenuItem value="">Unspecified</MenuItem>{salaryIntervals.map(item => <MenuItem key={item} value={item}>{item}</MenuItem>)}</TextField></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth disabled={disabled} type="datetime-local" label="Published at" value={value.publishedAt} onChange={event => set('publishedAt', event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { step: 0.001 } }} /></Grid>
    <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth disabled={disabled} type="datetime-local" label="Expires at" value={value.expiresAt} onChange={event => set('expiresAt', event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { step: 0.001 } }} /></Grid>
  </Grid>
}
