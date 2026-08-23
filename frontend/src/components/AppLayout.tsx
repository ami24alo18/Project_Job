import WorkOutlineIcon from '@mui/icons-material/WorkOutline'
import { AppBar, Box, Button, Container, Stack, Toolbar, Typography } from '@mui/material'
import type { PropsWithChildren } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
const links=[['Dashboard','/'],['Jobs','/jobs'],['Evaluations','/evaluations'],['Application drafts','/application-packages'],['Application review','/application-review'],['Handoffs','/application-handoffs'],['Matching','/matching/settings'],['Evaluation runs','/evaluation-runs'],['Quality','/evaluation-quality'],['Job sources','/job-sources'],['Source runs','/job-source-runs'],['Profile','/profile'],['Preferences','/preferences'],['Resume facts','/resume-facts'],['Reusable answers','/reusable-answers'],['Resume documents','/resume-documents'],['Published versions','/profile/versions']]
export function AppLayout({ children }: PropsWithChildren) {
 const {username,logout}=useAuth()
 return <Box sx={{minHeight:'100vh',bgcolor:'grey.50'}}><AppBar position="static" elevation={0}><Toolbar sx={{gap:1,flexWrap:'wrap'}}><WorkOutlineIcon/><Typography variant="h6" sx={{mr:2}}>Job Application Agent</Typography><Stack direction="row" spacing={.5} sx={{flex:1,overflowX:'auto'}}>{links.map(([label,to])=><Button key={to} component={NavLink} to={to} color="inherit" size="small">{label}</Button>)}</Stack><Typography variant="caption">{username}</Typography><Button color="inherit" onClick={logout}>Logout</Button></Toolbar></AppBar><Container maxWidth="lg" component="main" sx={{py:5}}>{children}</Container></Box>
}
