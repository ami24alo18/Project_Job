import RefreshIcon from '@mui/icons-material/Refresh'
import { Alert, Box, Button, Chip, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import { useEffect, useState } from 'react'
import { getSystemHealth } from '../api/systemApi'
import type { SystemHealth } from '../types/system'

type State = { phase: 'checking' } | { phase: 'online'; health: SystemHealth; checkedAt: Date } | { phase: 'offline'; checkedAt: Date }

export function HealthStatus() {
  const [state, setState] = useState<State>({ phase: 'checking' })
  const check = async () => {
    try {
      const health = await getSystemHealth()
      setState({ phase: 'online', health, checkedAt: new Date() })
    } catch {
      setState({ phase: 'offline', checkedAt: new Date() })
    }
  }
  useEffect(() => {
    let active = true
    getSystemHealth().then(
      (health) => { if (active) setState({ phase: 'online', health, checkedAt: new Date() }) },
      () => { if (active) setState({ phase: 'offline', checkedAt: new Date() }) },
    )
    return () => { active = false }
  }, [])

  const refresh = () => {
    setState({ phase: 'checking' })
    void check()
  }

  const label = state.phase === 'checking' ? 'Checking' : state.phase === 'online' ? 'Online' : 'Offline'
  const color = state.phase === 'online' ? 'success' : state.phase === 'offline' ? 'error' : 'default'
  return <Paper variant="outlined" sx={{ p: 3 }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={2}>
      <Box><Typography variant="h6" gutterBottom>Backend connectivity</Typography><Chip label={label} color={color} icon={state.phase === 'checking' ? <CircularProgress size={16} /> : undefined} />
        {state.phase === 'online' && <Typography sx={{ mt: 1.5 }}><strong>Service:</strong> {state.health.service}</Typography>}
        {state.phase !== 'checking' && <Typography variant="body2" color="text.secondary">Last checked: {state.checkedAt.toLocaleString()}</Typography>}
        {state.phase === 'offline' && <Alert severity="warning" sx={{ mt: 2 }}>The backend did not return a valid health response.</Alert>}
      </Box>
      <Button variant="outlined" startIcon={<RefreshIcon />} onClick={refresh} disabled={state.phase === 'checking'}>Refresh</Button>
    </Stack>
  </Paper>
}
