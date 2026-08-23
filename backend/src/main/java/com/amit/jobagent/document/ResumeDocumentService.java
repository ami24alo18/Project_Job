package com.amit.jobagent.document;
import com.amit.jobagent.audit.*;
import com.amit.jobagent.common.config.JobAgentProperties;
import com.amit.jobagent.common.error.*;
import com.amit.jobagent.profile.ActiveProfileProvider;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
@Service
public class ResumeDocumentService implements ActiveResumeDocumentProvider {
 private static final String PDF="application/pdf";private static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
 private final ResumeDocumentRepository repository;private final ActiveProfileProvider profiles;private final ObjectStorage storage;private final AuditService audit;private final long maximumSize;
 public ResumeDocumentService(ResumeDocumentRepository repository,ActiveProfileProvider profiles,ObjectStorage storage,AuditService audit,JobAgentProperties properties){this.repository=repository;this.profiles=profiles;this.storage=storage;this.audit=audit;maximumSize=properties.documents().maximumSizeBytes();}
 @Transactional public ResumeDocumentResponse upload(MultipartFile file){if(file.isEmpty())throw new DomainValidationException("A resume document is required");if(file.getSize()>maximumSize)throw new DocumentTooLargeException("The uploaded document exceeds the configured size limit");try{var bytes=file.getBytes();var original=file.getOriginalFilename()==null?"resume":file.getOriginalFilename();var sanitized=sanitize(original);var detected=new Tika().detect(bytes,original);var extension=sanitized.toLowerCase(Locale.ROOT);if(!(detected.equals(PDF)||detected.equals(DOCX))||detected.equals(PDF)&&!extension.endsWith(".pdf")||detected.equals(DOCX)&&!extension.endsWith(".docx"))throw new UnsupportedDocumentTypeException("Only genuine PDF and DOCX documents are accepted");var profileId=profiles.requireProfileId();var checksum=sha256(bytes);if(repository.findByProfileIdAndSha256ChecksumAndStatusNot(profileId,checksum,DocumentStatus.ARCHIVED).isPresent())throw new ConflictException("This resume document is already uploaded");var id=UUID.randomUUID();var key="profiles/"+profileId+"/resume-documents/"+id+"/"+sanitized;storage.store(key,bytes,detected);var document=new ResumeDocument(id,profileId,original,sanitized,detected,bytes.length,checksum,key);try{document.extracted(extract(bytes,original));}catch(Exception extraction){document.extractionFailed();}var saved=repository.saveAndFlush(document);audit.record(AuditEventType.RESUME_DOCUMENT_UPLOADED,"ResumeDocument",id,"{\"contentType\":\""+detected+"\"}");return map(saved);}catch(UnsupportedDocumentTypeException|ConflictException|DomainValidationException|DocumentTooLargeException|ResourceNotFoundException|StorageUnavailableException e){throw e;}catch(Exception e){throw new StorageUnavailableException("Unable to process document",e);}}
 @Transactional(readOnly=true)public List<ResumeDocumentResponse>list(){return repository.findByProfileIdOrderByCreatedAtDescIdAsc(profiles.requireProfileId()).stream().map(ResumeDocumentService::map).toList();}
 @Transactional(readOnly=true)public ResumeDocumentResponse get(UUID id){return map(requireOwned(id));}
 @Transactional(readOnly=true)public ExtractedTextResponse extractedText(UUID id){var d=requireOwned(id);return new ExtractedTextResponse(id,d.status(),d.extractedText(),d.extractionError());}
 @Transactional(readOnly=true)public DocumentDownload download(UUID id){var d=requireOwned(id);if(d.status()==DocumentStatus.ARCHIVED)throw new ResourceNotFoundException("Resume document was not found");return new DocumentDownload(d.sanitizedFileName(),d.contentType(),storage.load(d.storageKey()));}
 @Transactional public ResumeDocumentResponse activate(UUID id){var target=requireOwned(id);for(var current:repository.findByProfileIdAndActiveTrue(target.profileId()))if(!current.getId().equals(id))current.deactivate();repository.flush();target.activate();var saved=repository.saveAndFlush(target);audit.record(AuditEventType.RESUME_DOCUMENT_ACTIVATED,"ResumeDocument",id,"{}");return map(saved);}
 @Transactional public ResumeDocumentResponse archive(UUID id){var d=requireOwned(id);d.archive();storage.archive(d.storageKey());var saved=repository.saveAndFlush(d);audit.record(AuditEventType.RESUME_DOCUMENT_ARCHIVED,"ResumeDocument",id,"{}");return map(saved);}
 @Override @Transactional(readOnly=true)public Optional<ResumeDocumentResponse>activeMasterResume(){return repository.findFirstByProfileIdAndActiveTrueOrderByCreatedAtDesc(profiles.requireProfileId()).map(ResumeDocumentService::map);}
 private ResumeDocument requireOwned(UUID id){var profileId=profiles.requireProfileId();var d=repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Resume document was not found"));if(!d.profileId().equals(profileId))throw new ResourceNotFoundException("Resume document was not found");return d;}
 static String sanitize(String name){var base=name.replace('\\','/');base=base.substring(base.lastIndexOf('/')+1).replaceAll("[^A-Za-z0-9._-]","_").replaceAll("_+","_");if(base.isBlank()||base.equals(".")||base.equals(".."))base="resume";return base.length()>200?base.substring(base.length()-200):base;}
 static String sha256(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static String extract(byte[] bytes,String name)throws Exception{var metadata=new Metadata();metadata.set(org.apache.tika.metadata.TikaCoreProperties.RESOURCE_NAME_KEY,name);var handler=new BodyContentHandler(2_000_000);new AutoDetectParser().parse(new ByteArrayInputStream(bytes),handler,metadata,new ParseContext());return handler.toString().replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]","").replace("\u0000","").strip();}
 static ResumeDocumentResponse map(ResumeDocument d){return new ResumeDocumentResponse(d.getId(),d.profileId(),d.documentType(),d.originalFileName(),d.sanitizedFileName(),d.contentType(),d.sizeBytes(),d.sha256Checksum(),d.status(),d.active(),d.extractedText()!=null,d.extractionError(),d.getRecordVersion(),d.getCreatedAt(),d.getUpdatedAt());}
}
