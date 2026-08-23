import type { EmploymentType, JobInput, JobPosting, SalaryInterval, WorkplaceType } from '../types/jobs'
import { isSafeExternalUrl } from './urlSafety'

export interface JobFormState {
  company: string; title: string; location: string; countryCode: string; workplaceType: WorkplaceType; employmentType: EmploymentType
  department: string; team: string; description: string; applyUrl: string; sourceUrl: string
  salaryMinimum: string; salaryMaximum: string; salaryCurrency: string; salaryInterval: SalaryInterval | ''; publishedAt: string; expiresAt: string
}

export const emptyJobForm: JobFormState = { company: '', title: '', location: '', countryCode: '', workplaceType: 'UNSPECIFIED', employmentType: 'UNSPECIFIED', department: '', team: '', description: '', applyUrl: '', sourceUrl: '', salaryMinimum: '', salaryMaximum: '', salaryCurrency: '', salaryInterval: '', publishedAt: '', expiresAt: '' }

function localDateTime(value?: string) {
  if (!value) return ''
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return ''
  const pad = (part: number, length = 2) => String(part).padStart(length, '0')
  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())}T${pad(parsed.getHours())}:${pad(parsed.getMinutes())}:${pad(parsed.getSeconds())}.${pad(parsed.getMilliseconds(), 3)}`
}

export function jobToForm(job: JobPosting): JobFormState {
  return { company: job.company, title: job.title, location: job.location ?? '', countryCode: job.countryCode ?? '', workplaceType: job.workplaceType, employmentType: job.employmentType, department: job.department ?? '', team: job.team ?? '', description: job.descriptionPlainText ?? '', applyUrl: job.applyUrl ?? '', sourceUrl: job.sourceUrl ?? '', salaryMinimum: job.salaryMinimum?.toString() ?? '', salaryMaximum: job.salaryMaximum?.toString() ?? '', salaryCurrency: job.salaryCurrency ?? '', salaryInterval: job.salaryInterval ?? '', publishedAt: localDateTime(job.publishedAt), expiresAt: localDateTime(job.expiresAt) }
}

export function validateJobForm(form: JobFormState) {
  if (!form.company.trim() || !form.title.trim()) return 'Company and title are required'
  if (!form.description.trim() && !form.applyUrl.trim()) return 'Enter a description or an application URL'
  if (form.applyUrl && !isSafeExternalUrl(form.applyUrl)) return 'Application URL must use HTTP or HTTPS'
  if (form.sourceUrl && !isSafeExternalUrl(form.sourceUrl)) return 'Source URL must use HTTP or HTTPS'
  if (form.countryCode && !/^[A-Za-z]{2}$/.test(form.countryCode)) return 'Country code must contain two letters'
  if (form.salaryCurrency && !/^[A-Za-z]{3}$/.test(form.salaryCurrency)) return 'Salary currency must be a three-letter code'
  const minimum = form.salaryMinimum === '' ? undefined : Number(form.salaryMinimum)
  const maximum = form.salaryMaximum === '' ? undefined : Number(form.salaryMaximum)
  if ((minimum !== undefined && (!Number.isFinite(minimum) || minimum < 0)) || (maximum !== undefined && (!Number.isFinite(maximum) || maximum < 0))) return 'Salary values must be non-negative numbers'
  if (minimum !== undefined && maximum !== undefined && minimum > maximum) return 'Salary minimum cannot exceed salary maximum'
  return ''
}

export function jobFormToInput(form: JobFormState, recordVersion?: number): JobInput {
  const iso = (value: string) => value ? new Date(value).toISOString() : undefined
  return { company: form.company.trim(), title: form.title.trim(), location: form.location.trim() || undefined, countryCode: form.countryCode.trim().toUpperCase() || undefined, workplaceType: form.workplaceType, employmentType: form.employmentType, department: form.department.trim() || undefined, team: form.team.trim() || undefined, description: form.description.trim() || undefined, applyUrl: form.applyUrl.trim() || undefined, sourceUrl: form.sourceUrl.trim() || undefined, salaryMinimum: form.salaryMinimum === '' ? undefined : Number(form.salaryMinimum), salaryMaximum: form.salaryMaximum === '' ? undefined : Number(form.salaryMaximum), salaryCurrency: form.salaryCurrency.trim().toUpperCase() || undefined, salaryInterval: form.salaryInterval || undefined, publishedAt: iso(form.publishedAt), expiresAt: iso(form.expiresAt), recordVersion }
}
