import { Link, Typography } from '@mui/material'
import { isSafeExternalUrl } from '../utils/urlSafety'

export function SafeExternalLink({ href, children }: { href?: string; children: string }) {
  if (!isSafeExternalUrl(href)) return <Typography component="span" color="text.secondary">Unavailable</Typography>
  return <Link href={href} target="_blank" rel="noopener noreferrer">{children}</Link>
}
