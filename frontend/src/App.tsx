import { CssBaseline, ThemeProvider, createTheme } from '@mui/material'
import { BrowserRouter } from 'react-router-dom'
import { AppLayout } from './components/AppLayout'
import { AppRoutes } from './routes/AppRoutes'
import { AuthProvider } from './auth/AuthContext'

const theme = createTheme({ palette: { primary: { main: '#315c9b' }, background: { default: '#f7f9fc' } }, typography: { fontFamily: 'Inter, system-ui, sans-serif' }, shape: { borderRadius: 10 } })
export default function App() { return <ThemeProvider theme={theme}><CssBaseline /><AuthProvider><BrowserRouter><AppLayout><AppRoutes /></AppLayout></BrowserRouter></AuthProvider></ThemeProvider> }
