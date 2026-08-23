import { useEffect, useState, type FormEvent, type PropsWithChildren } from 'react'
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { apiClient } from '../api/apiClient'
import { clearCredentials, setCredentials } from './authStore'
import { AuthContext } from './useAuth'

export function AuthProvider({ children }: PropsWithChildren) {
  const [username, setUsername] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [checking, setChecking] = useState(false)
  useEffect(() => { const logout = () => setUsername(null); window.addEventListener('job-agent-logout', logout); return () => window.removeEventListener('job-agent-logout', logout) }, [])
  const login = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setChecking(true); setError('')
    const data = new FormData(event.currentTarget); const user = String(data.get('username') || ''); const password = String(data.get('password') || '')
    setCredentials(user, password)
    try { await apiClient.get('/api/v1/auth/check'); setUsername(user) } catch { clearCredentials(); setError('Authentication failed') } finally { setChecking(false) }
  }
  if (!username) return <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: 'grey.100', p: 2 }}><Paper component="form" onSubmit={login} sx={{ p: 4, width: '100%', maxWidth: 420 }}><Stack spacing={2}><Typography variant="h4">Job Application Agent</Typography><Typography color="text.secondary">Sign in with the temporary single-user credentials.</Typography>{error && <Alert severity="error">{error}</Alert>}<TextField name="username" label="Username" required autoComplete="username"/><TextField name="password" label="Password" type="password" required autoComplete="current-password"/><Button type="submit" variant="contained" disabled={checking}>{checking ? 'Signing in…' : 'Sign in'}</Button><Typography variant="caption">Credentials are kept in memory only. Refreshing requires sign-in again.</Typography></Stack></Paper></Box>
  return <AuthContext.Provider value={{ username, logout: clearCredentials }}>{children}</AuthContext.Provider>
}
