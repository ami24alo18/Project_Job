import axios from 'axios'
import { clearCredentials, getAuthorization } from '../auth/authStore'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 5000,
  headers: { Accept: 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const authorization = getAuthorization()
  if (authorization) config.headers.Authorization = authorization
  return config
})

apiClient.interceptors.response.use((response) => response, (error: unknown) => {
  if (axios.isAxiosError(error) && error.response?.status === 401 && getAuthorization()) clearCredentials()
  return Promise.reject(error)
})
