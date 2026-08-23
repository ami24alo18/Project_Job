package com.amit.jobagent.ai;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("job-agent.ai") public record AiEvaluationProperties(boolean enabled,String apiKey,String model,String reasoningEffort,int timeoutSeconds,int maxConcurrentRequests,int maxDailyRequests,long maxDailyInputTokens,int maxInputCharacters,int maxJobsPerBatch){public AiEvaluationProperties{if(model==null||model.isBlank())model="gpt-5.6-terra";if(reasoningEffort==null||reasoningEffort.isBlank())reasoningEffort="low";}}
