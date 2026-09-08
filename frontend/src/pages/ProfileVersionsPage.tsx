import { useCallback, useEffect, useState } from 'react'
import { Alert, Button, Chip, Paper, Stack, Typography } from '@mui/material'
import { profileApi } from '../api/profileApi'
import type { ProfileVersion } from '../types/profile'

export function ProfileVersionsPage() {
  const [items, setItems] = useState<ProfileVersion[]>([])
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<ProfileVersion | null>(null)
  const load = useCallback(() => profileApi.versions().then(setItems, () => setError('Could not load resume snapshots')), [])
  useEffect(() => { void load() }, [load])

  return <Stack spacing={3}>
    <Typography variant="h4">Automatic resume snapshots</Typography>
    <Alert severity="info">
      You do not need to publish a profile manually. Evaluating a job automatically creates an immutable snapshot from your active resume, profile, and preferences.
    </Alert>
    {error && <Alert severity="error">{error}</Alert>}
    {items.length === 0
      ? <Paper variant="outlined" sx={{ p: 3 }}><Typography color="text.secondary">No snapshots yet. Activate a resume and evaluate a job to create the first one.</Typography></Paper>
      : items.map(v => <Paper key={v.id} variant="outlined" sx={{ p: 2 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2}>
            <div>
              <Stack direction="row" spacing={1}><Typography variant="h6">Snapshot {v.versionNumber}</Typography>{v.active && <Chip label="Active" color="success" />}</Stack>
              <Typography>{v.changeReason}</Typography>
              <Typography variant="body2">{new Date(v.createdAt).toLocaleString()}</Typography>
              <Typography variant="caption" sx={{ wordBreak: 'break-all' }}>SHA-256: {v.checksum}</Typography>
            </div>
            <Button onClick={() => setSelected(v)}>View source evidence</Button>
          </Stack>
        </Paper>)}
    {selected && <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="h6">Snapshot {selected.versionNumber} evidence</Typography>
      <Typography component="pre" sx={{ whiteSpace: 'pre-wrap', overflow: 'auto', maxHeight: 600 }}>{JSON.stringify(selected.snapshot, null, 2)}</Typography>
    </Paper>}
  </Stack>
}
