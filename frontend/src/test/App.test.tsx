import { fireEvent,render,screen,waitFor } from '@testing-library/react'
import { beforeEach,describe,expect,it,vi } from 'vitest'
import App from '../App'
import * as systemApi from '../api/systemApi'
import { jobsApi } from '../api/jobsApi'
import { apiClient } from '../api/apiClient'
import { clearCredentials,getAuthorization,setCredentials } from '../auth/authStore'
vi.mock('../api/systemApi')
vi.mock('../api/jobsApi')
const health=vi.mocked(systemApi.getSystemHealth)
const summary=vi.mocked(jobsApi.summary)
async function login(){fireEvent.change(screen.getByLabelText(/Username/i),{target:{value:'owner'}});fireEvent.change(screen.getByLabelText(/Password/i),{target:{value:'secret'}});fireEvent.click(screen.getByRole('button',{name:'Sign in'}));await screen.findByRole('heading',{name:'Dashboard'})}
describe('application shell',()=>{beforeEach(()=>{window.history.pushState({},'','/');clearCredentials();health.mockReset();summary.mockReset();summary.mockResolvedValue({total:42,readyForEvaluation:20,needsReview:3,duplicates:4,expired:5,sourceRemoved:6,archived:4});vi.spyOn(apiClient,'get').mockResolvedValue({data:{username:'owner'}})})
 it('keeps credentials behind a login form',()=>{render(<App/>);expect(screen.getByRole('button',{name:'Sign in'})).toBeInTheDocument();expect(screen.getByText(/kept in memory only/i)).toBeInTheDocument()})
 it('renders dashboard, navigation, real job counts, and online health after login',async()=>{health.mockResolvedValue({status:'UP',service:'job-agent-backend',timestamp:'2026-01-01T00:00:00Z'});render(<App/>);await login();expect(screen.getByText('Discovered Jobs')).toBeInTheDocument();expect(await screen.findByText('42')).toBeInTheDocument();expect(screen.getByRole('link',{name:'Jobs'})).toHaveAttribute('href','/jobs');expect(screen.getByRole('link',{name:'Job sources'})).toHaveAttribute('href','/job-sources');expect(await screen.findByText('Online')).toBeInTheDocument()})
 it('replaces dashboard loading indicators with unavailable values after a summary failure',async()=>{health.mockResolvedValue({status:'UP',service:'job-agent-backend',timestamp:'2026-01-01T00:00:00Z'});summary.mockRejectedValue(new Error('temporary'));render(<App/>);await login();expect(await screen.findByText('Job counts are temporarily unavailable.')).toBeInTheDocument();expect(await screen.findAllByText('Unavailable')).toHaveLength(7);expect(screen.queryByLabelText('Loading Discovered Jobs')).not.toBeInTheDocument()})
 it('shows health loading state',async()=>{health.mockReturnValue(new Promise(()=>undefined));render(<App/>);await login();expect(screen.getByText('Checking')).toBeInTheDocument()})
 it('shows offline health failure',async()=>{health.mockRejectedValue(new Error('Network'));render(<App/>);await login();expect(await screen.findByText('Offline')).toBeInTheDocument()})
 it('refreshes health',async()=>{health.mockResolvedValue({status:'UP',service:'job-agent-backend',timestamp:'2026-01-01T00:00:00Z'});render(<App/>);await login();await screen.findByText('Online');fireEvent.click(screen.getByRole('button',{name:/refresh/i}));await waitFor(()=>expect(health).toHaveBeenCalledTimes(2))})
 it('renders unknown route',async()=>{window.history.pushState({},'','/missing');health.mockResolvedValue({status:'UP',service:'job-agent-backend',timestamp:'2026-01-01T00:00:00Z'});render(<App/>);fireEvent.change(screen.getByLabelText(/Username/i),{target:{value:'owner'}});fireEvent.change(screen.getByLabelText(/Password/i),{target:{value:'secret'}});fireEvent.click(screen.getByRole('button',{name:'Sign in'}));expect(await screen.findByRole('heading',{name:'Page not found'})).toBeInTheDocument()})
 it('clears in-memory credentials after an unauthorized API response',async()=>{vi.restoreAllMocks();setCredentials('owner','secret');await expect(apiClient.get('/fictional-protected-resource',{adapter:()=>Promise.reject({isAxiosError:true,response:{status:401}})})).rejects.toBeTruthy();expect(getAuthorization()).toBeNull()})
})
