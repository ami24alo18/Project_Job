import axios from 'axios'

interface ApiProblem {
  detail?: string
  fieldErrors?: Record<string, string>
}

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) return fallback
  const problem = error.response?.data as ApiProblem | undefined
  if (problem?.fieldErrors) {
    const messages = Object.values(problem.fieldErrors)
    if (messages.length) return messages.join(' · ')
  }
  return problem?.detail || fallback
}

export function isConflict(error: unknown) {
  return axios.isAxiosError(error) && error.response?.status === 409
}
