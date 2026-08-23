package com.amit.jobagent.ai;public record LlmJobEvaluationResult(LlmJobEvaluationResponse output,String responseId,Long inputTokens,Long outputTokens,Long totalTokens,long latencyMilliseconds){}
