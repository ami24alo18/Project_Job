import { Box, Typography } from '@mui/material'
import { useMemo } from 'react'
import { sanitizeResumePreview } from '../utils/resumePreviewSafety'

export function SafeResumePreview({ html }: { html: string }) {
  const sanitized = useMemo(() => sanitizeResumePreview(html), [html])
  if (!html.trim()) return <Typography color="text.secondary">The HTML preview is not available yet.</Typography>
  return <Box component="iframe" title="Tailored resume preview" sandbox="" referrerPolicy="no-referrer" srcDoc={sanitized}
    sx={{ width: '100%', minHeight: 680, border: 1, borderColor: 'divider', borderRadius: 1, bgcolor: 'white' }} />
}
