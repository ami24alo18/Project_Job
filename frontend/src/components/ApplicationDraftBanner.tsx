import { Alert } from '@mui/material'

export function ApplicationDraftBanner() {
  return <Alert severity="warning" variant="filled" sx={{ position: 'sticky', top: 8, zIndex: theme => theme.zIndex.appBar - 1 }}>
    Draft — not approved or submitted
  </Alert>
}
