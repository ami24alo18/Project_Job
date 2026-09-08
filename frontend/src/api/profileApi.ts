import { apiClient } from './apiClient'
import type { CandidateProfile, CandidateProfileInput, ProfileVersion, ResumeDocument, ReusableAnswer, SearchPreference } from '../types/profile'
export const profileApi = {
 getProfile: async () => (await apiClient.get<CandidateProfile>('/api/v1/profile')).data,
 saveProfile: async (body: CandidateProfileInput) => (await apiClient.put<CandidateProfile>('/api/v1/profile', body)).data,
 getPreferences: async () => (await apiClient.get<SearchPreference>('/api/v1/profile/preferences')).data,
 savePreferences: async (body: Omit<SearchPreference,'profileId'|'recordVersion'> & {recordVersion?:number}) => (await apiClient.put<SearchPreference>('/api/v1/profile/preferences',body)).data,
 answers: async () => (await apiClient.get<ReusableAnswer[]>('/api/v1/reusable-answers')).data,
 createAnswer: async (body:unknown) => (await apiClient.post<ReusableAnswer>('/api/v1/reusable-answers',body)).data,
 updateAnswer: async (id:string,body:unknown) => (await apiClient.put<ReusableAnswer>(`/api/v1/reusable-answers/${id}`,body)).data,
 answerAction: async (id:string,action:string) => (await apiClient.post<ReusableAnswer>(`/api/v1/reusable-answers/${id}/${action}`)).data,
 documents: async () => (await apiClient.get<ResumeDocument[]>('/api/v1/resume-documents')).data,
 upload: async (file:File,onUploadProgress?:(percent:number)=>void) => { const data=new FormData();data.append('file',file);return (await apiClient.post<ResumeDocument>('/api/v1/resume-documents',data,{onUploadProgress:e=>onUploadProgress?.(e.total?Math.round(e.loaded*100/e.total):0)})).data },
 documentAction: async (id:string,action:string) => (await apiClient.post<ResumeDocument>(`/api/v1/resume-documents/${id}/${action}`)).data,
 extractedText: async (id:string) => (await apiClient.get<{extractedText?:string;extractionError?:string}>(`/api/v1/resume-documents/${id}/extracted-text`)).data,
 download: async (id:string) => (await apiClient.get<Blob>(`/api/v1/resume-documents/${id}/download`,{responseType:'blob'})).data,
 versions: async () => (await apiClient.get<ProfileVersion[]>('/api/v1/profile/versions')).data,
 publish: async (changeReason:string) => (await apiClient.post<ProfileVersion>('/api/v1/profile/publish',{changeReason})).data,
}
