package com.amit.jobagent.jobsource;

import com.amit.jobagent.audit.AuditEventType;
import com.amit.jobagent.audit.AuditService;
import com.amit.jobagent.common.error.ConflictException;
import com.amit.jobagent.common.error.DomainValidationException;
import com.amit.jobagent.common.error.InvalidWebhookSecretException;
import com.amit.jobagent.common.error.ResourceNotFoundException;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.jobsource.connector.JobSourceConnector;
import com.amit.jobagent.jobsource.connector.SourceCheckpoint;
import com.amit.jobagent.jobsource.connector.SourceFetchRequest;
import com.amit.jobagent.jobsource.connector.SourceRegion;
import com.amit.jobagent.jobsource.discovery.CareerSiteDiscoveryRequest;
import com.amit.jobagent.jobsource.discovery.CareerSiteDiscoveryService;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobSourceConfigurationService{
    private final JobSourceConfigurationRepository repository;private final AuditService audit;private final WebhookTokenService tokens;private final CareerSiteDiscoveryService discovery;private final Map<JobSourceConnectorType,JobSourceConnector>connectors;private final Clock clock;private final ExtractionRecipeRegistry recipes;
    public JobSourceConfigurationService(JobSourceConfigurationRepository repository,AuditService audit,WebhookTokenService tokens,CareerSiteDiscoveryService discovery,List<JobSourceConnector>connectors,Clock clock,ExtractionRecipeRegistry recipes){this.repository=repository;this.audit=audit;this.tokens=tokens;this.discovery=discovery;this.connectors=new EnumMap<>(JobSourceConnectorType.class);connectors.forEach(connector->this.connectors.put(connector.supportedConnector()==null?JobSourceConnectorType.defaultFor(connector.supportedType()):connector.supportedConnector(),connector));this.clock=clock;this.recipes=recipes;}
    @Transactional public JobSourceConfigurationResponse create(JobSourceConfigurationRequest request){var normalized=validated(request);if(repository.existsBySourceTypeAndProviderIdentifierIgnoreCaseAndRegionAndArchivedAtIsNull(normalized.sourceType(),normalized.providerIdentifier(),normalized.region()))throw new ConflictException("An active configuration already exists for this provider");var saved=repository.saveAndFlush(new JobSourceConfiguration(normalized));audit.record(AuditEventType.JOB_SOURCE_CREATED,"JobSourceConfiguration",saved.getId(),"{\"sourceType\":\""+saved.sourceType()+"\"}");return map(saved);}
    @Transactional(readOnly=true)public List<JobSourceConfigurationResponse>list(){return repository.findAllByOrderByCreatedAtDescIdAsc().stream().map(JobSourceConfigurationService::map).toList();}
    @Transactional(readOnly=true)public JobSourceConfigurationResponse get(UUID id){return map(require(id));}
    @Transactional public JobSourceConfigurationResponse update(UUID id,JobSourceConfigurationRequest request){var source=require(id);checkVersion(source,request.recordVersion());var normalized=validated(request);if((source.sourceType()!=normalized.sourceType()||!source.providerIdentifier().equalsIgnoreCase(normalized.providerIdentifier())||source.region()!=normalized.region())&&repository.existsBySourceTypeAndProviderIdentifierIgnoreCaseAndRegionAndArchivedAtIsNull(normalized.sourceType(),normalized.providerIdentifier(),normalized.region()))throw new ConflictException("An active configuration already exists for this provider");source.apply(normalized,false);var saved=repository.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_UPDATED,"JobSourceConfiguration",id,"{}");return map(saved);}
    @Transactional public JobSourceConfigurationResponse enable(UUID id){var source=require(id);source.enable();var saved=repository.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_ENABLED,"JobSourceConfiguration",id,"{}");return map(saved);}
    @Transactional public JobSourceConfigurationResponse disable(UUID id){var source=require(id);source.disable();var saved=repository.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_DISABLED,"JobSourceConfiguration",id,"{}");return map(saved);}
    @Transactional public JobSourceConfigurationResponse archive(UUID id){var source=require(id);source.archive();var saved=repository.saveAndFlush(source);audit.record(AuditEventType.JOB_SOURCE_ARCHIVED,"JobSourceConfiguration",id,"{}");return map(saved);}
    @Transactional public ExternalJobSourceCreatedResponse createExternal(ExternalJobSourceRequest request){
        if(request.connectorType()!=JobSourceConnectorType.JSEARCH&&request.connectorType()!=JobSourceConnectorType.JOBSPY&&request.connectorType()!=JobSourceConnectorType.CUSTOM_WEBHOOK)throw new DomainValidationException("External sources support JSEARCH, JOBSPY, or CUSTOM_WEBHOOK connectors");
        var displayName=request.displayName().trim();var identifier=request.providerIdentifier().trim();
        if(repository.existsBySourceTypeAndProviderIdentifierIgnoreCaseAndRegionAndArchivedAtIsNull(JobSourceType.EXTERNAL_API,identifier,SourceRegion.DEFAULT))throw new ConflictException("An active configuration already exists for this provider");
        var token=tokens.issue();
        var saved=repository.saveAndFlush(JobSourceConfiguration.external(displayName,identifier,request.connectorType(),request.enabled()==null||request.enabled(),token.hash()));
        audit.record(AuditEventType.JOB_SOURCE_CREATED,"JobSourceConfiguration",saved.getId(),"{\"sourceType\":\"EXTERNAL_API\"}");
        return new ExternalJobSourceCreatedResponse(map(saved),webhookUrl(saved.getId()),token.plaintext());
    }
    @Transactional public JobSourceConfigurationResponse createCareerSite(CareerSiteJobSourceRequest request,String operator){
        var detected=discovery.discover(new CareerSiteDiscoveryRequest(request.companyName(),request.careerSiteUrl()),operator);
        if(detected.connectorType()==null)throw new DomainValidationException("The career site did not expose a recognizable source");
        if(repository.existsBySourceTypeAndProviderIdentifierIgnoreCaseAndRegionAndArchivedAtIsNull(JobSourceType.CAREER_SITE,detected.providerIdentifier(),SourceRegion.DEFAULT))throw new ConflictException("An active configuration already exists for this career site provider");
        var saved=repository.saveAndFlush(JobSourceConfiguration.careerSite(request,detected));
        audit.record(AuditEventType.JOB_SOURCE_CREATED,"JobSourceConfiguration",saved.getId(),"{\"sourceType\":\"CAREER_SITE\",\"connectorType\":\""+saved.connectorType()+"\"}");
        return map(saved);
    }
    @Transactional public JobSourceConnectionTestResponse testConnection(UUID id){
        var source=repository.findForUpdateById(id).orElseThrow(()->new ResourceNotFoundException("Job source was not found"));
        if(source.archivedAt()!=null||source.sourceType()!=JobSourceType.CAREER_SITE)throw new DomainValidationException("Only active career-site sources can be connection tested");
        var connector=connectors.get(source.connectorType());
        if(source.supportStatus()!=JobSourceSupportStatus.SUPPORTED||connector==null)throw new DomainValidationException("This career site does not have an enabled reviewed adapter");
        int count=0;JobSourceConnectionTestStatus status;String message;
        try{var result=connector.fetch(new SourceFetchRequest(source.sourceType(),source.connectorType(),source.providerIdentifier(),source.region(),source.careerSiteUrl(),source.canonicalHost(),Math.min(source.pageSize(),10),1,SourceCheckpoint.beginning()));count=result.discoveredCount();status=JobSourceConnectionTestStatus.SUCCEEDED;message="The reviewed adapter returned a valid bounded response";}
        catch(RuntimeException failure){status=JobSourceConnectionTestStatus.FAILED;message="The reviewed adapter could not validate the remote source";}
        source.connectionTested(clock.instant(),status);var saved=repository.saveAndFlush(source);
        audit.record(AuditEventType.JOB_SOURCE_CONNECTION_TESTED,"JobSourceConfiguration",id,"{\"status\":\""+status+"\"}");
        return new JobSourceConnectionTestResponse(map(saved),status,count,message);
    }
    @Transactional public WebhookTokenRotationResponse rotateToken(UUID id){
        var source=repository.findForUpdateById(id).orElseThrow(()->new ConflictException("The source cannot rotate a token"));
        if(source.archivedAt()!=null||!(source.sourceType()==JobSourceType.EXTERNAL_API||source.sourceType()==JobSourceType.CAREER_SITE&&source.connectorType()==JobSourceConnectorType.CUSTOM_RECIPE&&source.webhookConfigured()))throw new ConflictException("The source cannot rotate a token");
        var token=tokens.issue();source.rotateWebhookToken(token.hash());repository.saveAndFlush(source);
        audit.record(AuditEventType.JOB_SOURCE_UPDATED,"JobSourceConfiguration",id,"{\"tokenRotated\":true}");
        return new WebhookTokenRotationResponse(id,webhookUrl(id),token.plaintext());
    }
    @Transactional(readOnly=true) AuthenticatedExternalSource authenticateExternal(UUID id,String suppliedToken){
        var source=repository.findById(id).orElseThrow(InvalidWebhookSecretException::new);
        boolean webhookSource=source.sourceType()==JobSourceType.EXTERNAL_API||source.sourceType()==JobSourceType.CAREER_SITE&&source.connectorType()==JobSourceConnectorType.CUSTOM_RECIPE;
        if(!webhookSource||!tokens.matches(suppliedToken,source.webhookTokenHash()))throw new InvalidWebhookSecretException();
        if(source.archivedAt()!=null||!source.enabled())throw new ConflictException("The external source is not enabled");
        return new AuthenticatedExternalSource(source.getId(),source.sourceType(),source.connectorType(),source.displayName(),source.extractionRecipeVersion());
    }
    @Transactional public ExtractionRecipeAssociationResponse associateRecipe(UUID id,ExtractionRecipeAssociationRequest request){
        var source=repository.findForUpdateById(id).orElseThrow(()->new ResourceNotFoundException("Job source was not found"));
        var recipe=recipes.requireCompatible(request.recipeId(),source.canonicalHost(),source.careerSiteUrl());
        var token=tokens.issue();source.associateRecipe(recipe.version(),token.hash(),request.enabled()==null||request.enabled());var saved=repository.saveAndFlush(source);
        audit.record(AuditEventType.JOB_SOURCE_RECIPE_ASSOCIATED,"JobSourceConfiguration",id,"{\"recipeId\":\""+recipe.id()+"\",\"recipeVersion\":\""+recipe.version()+"\"}");
        return new ExtractionRecipeAssociationResponse(map(saved),webhookUrl(id),token.plaintext());
    }
    @Transactional(readOnly=true)JobSourceConfiguration requireEntity(UUID id){return require(id);}
    @Transactional(readOnly=true)List<JobSourceConfiguration>enabledRemote(){return repository.findByEnabledTrueAndArchivedAtIsNullAndSourceTypeInOrderByCreatedAtAscIdAsc(List.of(JobSourceType.LEVER,JobSourceType.GREENHOUSE,JobSourceType.CAREER_SITE)).stream().filter(JobSourceConfigurationService::isPullSource).toList();}
    @Transactional(readOnly=true)JobSourceConfiguration emailSource(String name){return repository.findBySourceTypeAndProviderIdentifierIgnoreCaseAndArchivedAtIsNull(JobSourceType.EMAIL_WEBHOOK,name).filter(JobSourceConfiguration::enabled).orElseThrow(()->new ResourceNotFoundException("Enabled email-alert source was not found"));}
    private JobSourceConfiguration require(UUID id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Job source was not found"));}
    private static void checkVersion(JobSourceConfiguration source,Long version){if(version==null||version!=source.getRecordVersion())throw new ConflictException("Job source was updated by another request");}
    private static String webhookUrl(UUID id){return "/api/v1/job-sources/"+id+"/external-events";}
    private static boolean isPullSource(JobSourceConfiguration source){return switch(source.connectorType()){case LEVER,GREENHOUSE,SMARTRECRUITERS,GENERIC_JSON_LD->true;default->false;};}
    private static JobSourceConfigurationRequest validated(JobSourceConfigurationRequest r){
        if(r.sourceType()==JobSourceType.MANUAL)throw new DomainValidationException("Manual jobs do not use a source configuration");
        if(r.sourceType()==JobSourceType.EXTERNAL_API||r.sourceType()==JobSourceType.CAREER_SITE)throw new DomainValidationException("Use the dedicated setup endpoint for this source type");
        var identifier=r.providerIdentifier().trim();
        var validPattern=r.sourceType()==JobSourceType.EMAIL_WEBHOOK
                ?"[A-Za-z0-9][A-Za-z0-9 ._-]{0,199}"
                :"[A-Za-z0-9][A-Za-z0-9._-]{0,199}";
        if(!identifier.matches(validPattern)||identifier.contains("://")||identifier.contains("/")||identifier.contains("\\\\"))throw new DomainValidationException("Provider identifier must be a site, board token, or logical source name, not a URL");
        var region=r.region();
        if(r.sourceType()==JobSourceType.LEVER){
            if(region==SourceRegion.DEFAULT)region=SourceRegion.GLOBAL;
            if(region!=SourceRegion.GLOBAL&&region!=SourceRegion.EU)throw new DomainValidationException("Lever supports GLOBAL or EU regions");
        }else if(region!=SourceRegion.DEFAULT)throw new DomainValidationException("This source type supports only the DEFAULT region");
        return new JobSourceConfigurationRequest(r.displayName(),r.sourceType(),identifier,region,r.enabled()==null?Boolean.TRUE:r.enabled(),r.pageSize(),r.maximumPagesPerRun(),r.missingRunThreshold(),r.recordVersion());
    }
    static JobSourceConfigurationResponse map(JobSourceConfiguration s){return new JobSourceConfigurationResponse(
            s.getId(),s.displayName(),s.sourceType(),s.sourceCategory(),s.connectorType(),s.providerIdentifier(),s.region(),
            s.careerSiteUrl(),s.canonicalHost(),s.supportStatus(),s.supportMessage(),s.webhookConfigured(),s.detectionVersion(),
            s.extractionRecipeVersion(),s.lastConnectionTestAt(),s.lastConnectionTestStatus(),s.enabled(),s.pageSize(),
            s.maximumPagesPerRun(),s.missingRunThreshold(),s.lastSuccessfulSyncAt(),s.lastAttemptedSyncAt(),
            s.consecutiveFailureCount(),s.getRecordVersion(),s.getCreatedAt(),s.getUpdatedAt(),s.archivedAt());}
}
