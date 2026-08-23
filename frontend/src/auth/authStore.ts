let authorization: string | null = null
export function setCredentials(username: string, password: string) { authorization = `Basic ${btoa(`${username}:${password}`)}` }
export function clearCredentials() { authorization = null; window.dispatchEvent(new Event('job-agent-logout')) }
export function getAuthorization() { return authorization }
