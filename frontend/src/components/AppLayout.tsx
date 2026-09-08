import MenuIcon from '@mui/icons-material/Menu'
import WorkOutlineIcon from '@mui/icons-material/WorkOutline'
import {
  AppBar,
  Box,
  Button,
  Container,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  ListSubheader,
  Stack,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { type PropsWithChildren, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const drawerWidth = 272

const navigation = [
  {
    label: 'Workspace',
    links: [
      ['Dashboard', '/'],
      ['Jobs', '/jobs'],
      ['Evaluations', '/evaluations'],
    ],
  },
  {
    label: 'Applications',
    links: [
      ['Application drafts', '/application-packages'],
      ['Application review', '/application-review'],
      ['Handoffs', '/application-handoffs'],
      ['Evaluation runs', '/evaluation-runs'],
      ['Evaluation quality', '/evaluation-quality'],
    ],
  },
  {
    label: 'Job discovery',
    links: [
      ['Matching settings', '/matching/settings'],
      ['Job sources', '/job-sources'],
      ['Source runs', '/job-source-runs'],
    ],
  },
  {
    label: 'Candidate profile',
    links: [
      ['Profile', '/profile'],
      ['Preferences', '/preferences'],
      ['Reusable answers', '/reusable-answers'],
      ['Resume documents', '/resume-documents'],
      ['Resume snapshots', '/profile/versions'],
    ],
  },
] as const

export function AppLayout({ children }: PropsWithChildren) {
  const { username, logout } = useAuth()
  const theme = useTheme()
  const desktop = useMediaQuery(theme.breakpoints.up('md'), { defaultMatches: true })
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false)
  const navigationOpen = desktop || mobileNavigationOpen

  const drawer = (
    <>
      <Toolbar />
      <Box component="nav" aria-label="Main navigation" sx={{ overflowY: 'auto', py: 1 }}>
        {navigation.map((group, index) => (
          <Box key={group.label}>
            {index > 0 && <Divider sx={{ my: 1 }} />}
            <List
              dense
              subheader={
                <ListSubheader
                  component="div"
                  sx={{ bgcolor: 'background.paper', fontWeight: 700, lineHeight: '32px' }}
                >
                  {group.label}
                </ListSubheader>
              }
            >
              {group.links.map(([label, to]) => (
                <ListItemButton
                  key={to}
                  component={NavLink}
                  to={to}
                  end={to === '/'}
                  onClick={() => setMobileNavigationOpen(false)}
                  sx={{
                    mx: 1,
                    borderRadius: 1,
                    '&.active': {
                      bgcolor: 'primary.main',
                      color: 'primary.contrastText',
                      '&:hover': { bgcolor: 'primary.dark' },
                    },
                  }}
                >
                  <ListItemText primary={label} />
                </ListItemButton>
              ))}
            </List>
          </Box>
        ))}
      </Box>
    </>
  )

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'grey.50', display: 'flex' }}>
      <AppBar position="fixed" elevation={0} sx={{ zIndex: theme.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 1, flexWrap: 'nowrap' }}>
          {!desktop && (
            <IconButton
              color="inherit"
              edge="start"
              aria-label="Open navigation"
              onClick={() => setMobileNavigationOpen(true)}
            >
              <MenuIcon />
            </IconButton>
          )}
          <WorkOutlineIcon />
          <Typography
            variant="h6"
            component="div"
            noWrap
            sx={{ flexGrow: 1, fontSize: { xs: '1rem', sm: '1.25rem' } }}
          >
            Job Application Agent
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ flexShrink: 0 }}>
            <Typography variant="body2" noWrap sx={{ display: { xs: 'none', sm: 'block' } }}>
              {username}
            </Typography>
            <Button color="inherit" onClick={logout}>
              Logout
            </Button>
          </Stack>
        </Toolbar>
      </AppBar>

      <Drawer
        variant={desktop ? 'permanent' : 'temporary'}
        open={navigationOpen}
        onClose={() => setMobileNavigationOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{
          width: desktop ? drawerWidth : 0,
          flexShrink: 0,
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
          },
        }}
      >
        {drawer}
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0 }}>
        <Toolbar />
        <Container maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
          {children}
        </Container>
      </Box>
    </Box>
  )
}
