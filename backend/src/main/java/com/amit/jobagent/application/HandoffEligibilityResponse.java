package com.amit.jobagent.application;import java.util.List;public record HandoffEligibilityResponse(boolean eligible,List<BlockingReason>blockingReasons){}
