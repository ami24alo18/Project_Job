import DownloadIcon from '@mui/icons-material/Download'
import EditIcon from '@mui/icons-material/Edit'
import FactCheckIcon from '@mui/icons-material/FactCheck'
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  Drawer,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMemo, useState, type FormEvent } from 'react'
import { applicationPackagesApi } from '../api/applicationPackagesApi'
import { apiErrorMessage, isConflict } from '../api/apiErrors'
import type {
  ApplicationPackageRevision,
  ApplicationQuestionDraft,
  DocumentArtifactType,
  GeneratedClaim,
  GeneratedContent,
  GenerationWarning,
  UnsupportedRequirement,
} from '../types/applicationPackages'
import { SafeResumePreview } from './SafeResumePreview'

const contentLabels: Record<string, string> = {
  RESUME_HEADLINE: 'Resume headline',
  PROFESSIONAL_SUMMARY: 'Professional summary',
  SKILL_SECTION: 'Skills',
  EXPERIENCE_BULLET: 'Experience',
  PROJECT_BULLET: 'Projects',
  EDUCATION_SECTION: 'Education',
  COVER_LETTER: 'Cover letter',
  RECRUITER_MESSAGE: 'Recruiter message',
  APPLICATION_ANSWER: 'Application answer',
}

function contentLabel(content: GeneratedContent) {
  return contentLabels[content.contentType] ?? content.contentType.replaceAll('_', ' ').toLowerCase()
}

function warningText(warning: GenerationWarning | string) {
  return typeof warning === 'string' ? warning : [warning.code, warning.message].filter(Boolean).join(': ')
}

function requirementText(requirement: UnsupportedRequirement | string) {
  if (typeof requirement === 'string') return requirement
  return [requirement.requirement ?? requirement.requirementId, requirement.reason].filter(Boolean).join(' — ')
}

function classificationColor(classification: ApplicationQuestionDraft['classification']): 'success' | 'info' | 'warning' | 'error' {
  if (classification === 'VERIFIED_AUTOMATIC') return 'success'
  if (classification === 'SUGGESTED_REQUIRES_REVIEW') return 'info'
  if (classification === 'USER_INPUT_REQUIRED') return 'warning'
  return 'error'
}

interface ContentCardProps {
  content: GeneratedContent
  claims: GeneratedClaim[]
  editable: boolean
  onUpdated: (content: GeneratedContent) => void
  onEvidence: (contentId: string) => void
  packageId: string
}

function ContentCard({ content, claims, editable, onUpdated, onEvidence, packageId }: ContentCardProps) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(content.text)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const label = contentLabel(content)
  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!text.trim()) { setError('Content cannot be empty'); return }
    setSaving(true); setError('')
    try {
      const updated = await applicationPackagesApi.updateContent(packageId, content.id, { text: text.trim(), recordVersion: content.recordVersion })
      onUpdated(updated); setEditing(false)
    } catch (problem) {
      setError(isConflict(problem) ? 'This section changed elsewhere. Reload before saving.' : apiErrorMessage(problem, 'This section could not be saved'))
    } finally { setSaving(false) }
  }
  return <Paper variant="outlined" sx={{ p: 2 }}>
    <Stack spacing={1.5}>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}>
        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
          <Typography variant="h6">{label}</Typography>
          {(content.userEdited || content.origin === 'USER_EDITED') && <Chip size="small" color="info" label="User edited" />}
          <Chip size="small" variant="outlined" label={content.verificationStatus} color={content.verificationStatus === 'VERIFIED' ? 'success' : 'warning'} />
        </Stack>
        <Stack direction="row">
          {claims.length > 0 && <Button size="small" startIcon={<FactCheckIcon />} onClick={() => onEvidence(content.id)}>Evidence ({claims.length})</Button>}
          {editable && !editing && <Button size="small" startIcon={<EditIcon />} onClick={() => { setText(content.text); setEditing(true) }}>Edit {label.toLowerCase()}</Button>}
        </Stack>
      </Stack>
      {error && <Alert severity="error">{error}</Alert>}
      {editing ? <Box component="form" onSubmit={save}>
        <Stack spacing={1}>
          <TextField fullWidth multiline minRows={3} label={`Edit ${label.toLowerCase()}`} value={text} onChange={event => setText(event.target.value)} inputProps={{ maxLength: 12_000 }} />
          <Stack direction="row" spacing={1}>
            <Button type="submit" variant="contained" disabled={saving}>Save section</Button>
            <Button disabled={saving} onClick={() => { setText(content.text); setEditing(false); setError('') }}>Cancel</Button>
          </Stack>
          <Typography variant="caption" color="text.secondary">Edited claims lose automatic verification until the server validates them again.</Typography>
        </Stack>
      </Box> : <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{content.text}</Typography>}
    </Stack>
  </Paper>
}

interface Props {
  packageId: string
  revision: ApplicationPackageRevision
  editable: boolean
  previewHtml?: string
  previewError?: string
  onRevisionChange: (revision: ApplicationPackageRevision, reason?: 'CONTENT_EDIT') => void
  reviewRevisionId?: string
}

export function ApplicationRevisionInspector({ packageId, revision, editable, previewHtml, previewError, onRevisionChange, reviewRevisionId }: Props) {
  const [evidenceFor, setEvidenceFor] = useState<string | null>(null)
  const [questionText, setQuestionText] = useState('')
  const [questionBusy, setQuestionBusy] = useState(false)
  const [questionError, setQuestionError] = useState('')
  const [downloadBusy, setDownloadBusy] = useState<DocumentArtifactType | null>(null)
  const [downloadError, setDownloadError] = useState('')
  const contents = useMemo(() => [...(revision.contents ?? [])].sort((a, b) => a.sectionOrder - b.sectionOrder || a.contentKey.localeCompare(b.contentKey)), [revision.contents])
  const visibleClaims = evidenceFor === '*' ? revision.claims : revision.claims.filter(claim => claim.generatedContentId === evidenceFor)
  const updateContent = (updated: GeneratedContent) => onRevisionChange({
    ...revision,
    status: 'STALE',
    contents: revision.contents.map(item => item.id === updated.id ? updated : item),
    claims: revision.claims.map(claim => claim.generatedContentId === updated.id
      ? { ...claim, validationStatus: 'REQUIRES_REVIEW', validationCodes: ['USER_EDITED_CONTENT'] }
      : claim),
  }, 'CONTENT_EDIT')
  const replaceQuestions = (questions: ApplicationQuestionDraft[]) => onRevisionChange({ ...revision, questions })
  const addQuestions = async () => {
    const questions = questionText.split(/\r?\n/).map(value => value.trim()).filter(Boolean)
    if (!questions.length) { setQuestionError('Enter at least one application question'); return }
    setQuestionBusy(true); setQuestionError('')
    try { replaceQuestions(await applicationPackagesApi.addQuestions(packageId, questions)); setQuestionText('') }
    catch (problem) { setQuestionError(apiErrorMessage(problem, 'Application questions could not be classified')) }
    finally { setQuestionBusy(false) }
  }
  const draftQuestions = async () => {
    setQuestionBusy(true); setQuestionError('')
    try { replaceQuestions(await applicationPackagesApi.draftQuestions(packageId)) }
    catch (problem) { setQuestionError(apiErrorMessage(problem, 'Question drafts could not be generated')) }
    finally { setQuestionBusy(false) }
  }
  const download = async (type: Exclude<DocumentArtifactType, 'HTML_PREVIEW'>) => {
    setDownloadBusy(type); setDownloadError('')
    try { if(reviewRevisionId) await applicationPackagesApi.revisionDownload(packageId,reviewRevisionId,type); else await applicationPackagesApi.download(packageId, type) }
    catch (problem) { setDownloadError(apiErrorMessage(problem, `${type === 'PDF_RESUME' ? 'PDF' : 'DOCX'} download failed`)) }
    finally { setDownloadBusy(null) }
  }
  const artifactReady = (type: DocumentArtifactType) => revision.artifacts?.some(artifact => artifact.artifactType === type && artifact.renderingStatus === 'READY') ?? false
  return <Stack spacing={3}>
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Source profile version</Typography><Typography sx={{ overflowWrap: 'anywhere' }}>{revision.profileVersionId}</Typography></Grid>
        <Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Source evaluation</Typography><Typography sx={{ overflowWrap: 'anywhere' }}>{revision.evaluationId}</Typography></Grid>
        <Grid size={{ xs: 12, md: 4 }}><Typography variant="overline">Generation</Typography><Typography>{revision.model} · {revision.generationPromptVersion} / {revision.generationSchemaVersion}</Typography></Grid>
      </Grid>
    </Paper>

    {(revision.warnings?.length ?? 0) > 0 && <Alert severity="warning"><Typography fontWeight={600}>Generation warnings</Typography><List dense disablePadding>{revision.warnings?.map((warning, index) => <ListItem key={index} disableGutters><ListItemText primary={warningText(warning)} /></ListItem>)}</List></Alert>}
    {(revision.unsupportedRequirements?.length ?? 0) > 0 && <Alert severity="warning"><Typography fontWeight={600}>Unsupported job requirements — not added as candidate skills</Typography><List dense disablePadding>{revision.unsupportedRequirements?.map((requirement, index) => <ListItem key={index} disableGutters><ListItemText primary={requirementText(requirement)} /></ListItem>)}</List></Alert>}

    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ sm: 'center' }} spacing={1}>
      <Typography variant="h5">Tailored content</Typography>
      {revision.claims.length > 0 && <Button startIcon={<FactCheckIcon />} onClick={() => setEvidenceFor('*')}>All evidence and provenance</Button>}
    </Stack>
    {contents.length === 0 ? <Alert severity="info">Structured content will appear after generation completes.</Alert> : contents.map(content => <ContentCard key={content.id} content={content} claims={revision.claims.filter(claim => claim.generatedContentId === content.id)} editable={editable} packageId={packageId} onUpdated={updateContent} onEvidence={setEvidenceFor} />)}

    <Divider />
    <Typography variant="h5">Resume preview</Typography>
    {previewError && <Alert severity="warning">{previewError}</Alert>}
    {previewHtml === undefined && !previewError ? <Typography color="text.secondary">Loading protected HTML preview…</Typography> : <SafeResumePreview html={previewHtml ?? ''} />}
    {downloadError && <Alert severity="error">{downloadError}</Alert>}
    <Stack direction="row" spacing={1} flexWrap="wrap">
      <Button variant="contained" startIcon={<DownloadIcon />} disabled={(!editable&&!reviewRevisionId) || !artifactReady('PDF_RESUME') || downloadBusy !== null} onClick={() => void download('PDF_RESUME')}>Download PDF</Button>
      <Button variant="outlined" startIcon={<DownloadIcon />} disabled={(!editable&&!reviewRevisionId) || !artifactReady('DOCX_RESUME') || downloadBusy !== null} onClick={() => void download('DOCX_RESUME')}>Download DOCX</Button>
      {!editable && !reviewRevisionId && <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>Downloads on a historical page are disabled to avoid returning the current revision by mistake.</Typography>}
    </Stack>

    <Divider />
    <Typography variant="h5">Application-question drafts</Typography>
    {questionError && <Alert severity="error">{questionError}</Alert>}
    {revision.questions.length === 0 ? <Typography color="text.secondary">No application questions have been added.</Typography> : revision.questions.map(question => <Paper key={question.id} variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={1}>
        <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" spacing={1}><Typography fontWeight={600}>{question.question}</Typography><Chip size="small" color={classificationColor(question.classification)} label={question.classification} /></Stack>
        {question.classification === 'SENSITIVE_NEVER_AUTOMATIC' ? <Alert severity="error">No answer generated by policy. This sensitive question requires personal confirmation.</Alert> : question.draftAnswer ? <Typography sx={{ whiteSpace: 'pre-wrap' }}>{question.draftAnswer}</Typography> : <Typography color="text.secondary">No automatic answer. {question.classification === 'USER_INPUT_REQUIRED' ? 'Candidate input is required.' : 'A draft has not been generated.'}</Typography>}
        {question.confidence !== undefined && <Typography variant="caption" color="text.secondary">Confidence: {Math.round(question.confidence * 100)}%</Typography>}
      </Stack>
    </Paper>)}
    {editable && <Paper variant="outlined" sx={{ p: 2 }}><Stack spacing={1.5}><TextField multiline minRows={3} label="Application questions (one per line)" value={questionText} onChange={event => setQuestionText(event.target.value)} inputProps={{ maxLength: 20_000 }} /><Stack direction="row" spacing={1} flexWrap="wrap"><Button variant="outlined" disabled={questionBusy} onClick={() => void addQuestions()}>Classify questions</Button><Button variant="contained" disabled={questionBusy || revision.questions.length === 0} onClick={() => void draftQuestions()}>Draft eligible answers</Button></Stack><Typography variant="caption" color="text.secondary">Sensitive, compensation, authorization, declarations, and other decision-dependent questions are never inferred.</Typography></Stack></Paper>}

    <Drawer anchor="right" open={evidenceFor !== null} onClose={() => setEvidenceFor(null)}>
      <Box role="dialog" aria-label="Evidence and provenance" sx={{ width: { xs: 320, sm: 480 }, maxWidth: '100vw', p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="h5">Evidence and provenance</Typography><Tooltip title="Close"><IconButton aria-label="Close evidence" onClick={() => setEvidenceFor(null)}>×</IconButton></Tooltip></Stack>
        <Alert severity="info" sx={{ my: 2 }}>Candidate claims must point to verified facts from this exact immutable profile version.</Alert>
        {visibleClaims.length === 0 ? <Typography color="text.secondary">No factual claims are attached to this section.</Typography> : visibleClaims.map(claim => <Paper key={claim.id} variant="outlined" sx={{ p: 2, mb: 2 }}>
          <Stack spacing={1}><Typography fontWeight={600}>{claim.claimText}</Typography><Stack direction="row" spacing={1} flexWrap="wrap"><Chip size="small" label={claim.claimType} /><Chip size="small" label={claim.validationStatus} color={claim.validationStatus === 'VALID' ? 'success' : 'warning'} /></Stack><Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>Content path: {claim.contentPath}</Typography>{claim.validationCodes.length > 0 && <Typography variant="body2">Validation: {claim.validationCodes.join(', ')}</Typography>}{claim.sources.length === 0 ? <Typography variant="body2" color="text.secondary">No source IDs (allowed only for explicitly non-factual language).</Typography> : claim.sources.map((source, index) => <Box key={index} sx={{ pl: 1, borderLeft: 3, borderColor: 'primary.light' }}>{source.candidateFactId && <Typography variant="body2">Candidate fact: {source.candidateFactId}</Typography>}{source.jobRequirementId && <Typography variant="body2">Job requirement: {source.jobRequirementId}</Typography>}{source.jobFieldReference && <Typography variant="body2">Job field: {source.jobFieldReference}</Typography>}</Box>)}</Stack>
        </Paper>)}
      </Box>
    </Drawer>
  </Stack>
}
