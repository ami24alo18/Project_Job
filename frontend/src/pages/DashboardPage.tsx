import { Alert, CircularProgress, Grid, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { HealthStatus } from '../components/HealthStatus'
import { jobsApi } from '../api/jobsApi'
import type { JobSummary } from '../types/jobs'

export function DashboardPage() {
  const [summary, setSummary] = useState<JobSummary | null>(null)
  const [summaryError, setSummaryError] = useState(false)
  useEffect(() => {
    let active = true
    jobsApi.summary().then(value => { if (active) setSummary(value) }, () => { if (active) setSummaryError(true) })
    return () => { active = false }
  }, [])
  const jobCards: [string, keyof JobSummary][] = [['Discovered Jobs', 'total'], ['Ready for Evaluation', 'readyForEvaluation'], ['Needs Review', 'needsReview'], ['Duplicates', 'duplicates'], ['Expired', 'expired'], ['Source Removed', 'sourceRemoved'], ['Archived', 'archived']]
  return <Stack spacing={4}>
    <div><Typography variant="h4" component="h1" gutterBottom>Dashboard</Typography><Typography color="text.secondary">Current status for your self-hosted application workspace.</Typography></div>
    <HealthStatus />
    {summaryError && <Alert severity="warning">Job counts are temporarily unavailable.</Alert>}
    <Grid container spacing={2}>{jobCards.map(([name, key]) => <Grid key={name} size={{ xs: 12, sm: 6, md: 3 }}><Paper variant="outlined" sx={{ p: 3, height: '100%' }}><Typography variant="h6">{name}</Typography><Typography variant="h4" sx={{ mt: 1 }}>{summary ? (summary[key] ?? 0) : summaryError ? <Typography component="span" color="text.secondary">Unavailable</Typography> : <CircularProgress size={24} aria-label={`Loading ${name}`} />}</Typography></Paper></Grid>)}{['Applications', 'Interviews'].map(name => <Grid key={name} size={{ xs: 12, sm: 6, md: 3 }}><Paper variant="outlined" sx={{ p: 3, height: '100%' }}><Typography variant="h6">{name}</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Future functionality</Typography></Paper></Grid>)}</Grid>
  </Stack>
}
