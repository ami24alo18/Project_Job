import { Button, Stack, Typography } from '@mui/material'
import { Link } from 'react-router-dom'
export function NotFoundPage() { return <Stack spacing={2} alignItems="flex-start"><Typography variant="h3">Page not found</Typography><Typography>The requested page does not exist.</Typography><Button component={Link} to="/" variant="contained">Return to dashboard</Button></Stack> }
