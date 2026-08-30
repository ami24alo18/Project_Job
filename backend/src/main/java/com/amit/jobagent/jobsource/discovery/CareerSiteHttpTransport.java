package com.amit.jobagent.jobsource.discovery;

@FunctionalInterface
interface CareerSiteHttpTransport {
    CareerSiteHttpResponse get(ValidatedCareerSiteTarget target);
}
