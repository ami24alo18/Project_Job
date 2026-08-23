package com.amit.jobagent.matching;import java.time.Instant;import java.util.*;
public record RuleEvaluationResponse(UUID id,UUID jobId,UUID profileVersionId,String rulesetVersion,RuleResult result,List<RuleReasonCode>reasonCodes,Map<String,String>safeEvidence,boolean cacheHit,Instant createdAt){}
