package com.amit.jobagent.jobsource;

import java.util.List;

public record JobSpyPolicyResponse(boolean configured, List<String> allowedSites) {}
