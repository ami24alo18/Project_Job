package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.*;
import com.amit.jobagent.jobsource.connector.RawJobRecord;
import org.springframework.stereotype.Component;

@Component
class GreenhouseJobSourceNormalizer implements JobSourceNormalizer{
    public JobSourceType supportedType(){return JobSourceType.GREENHOUSE;}
    public JobCandidate normalize(RawJobRecord r,JobSourceRunContext s){return new JobCandidate(s.sourceId(),supportedType(),r.externalId(),r.company()==null?s.displayName():r.company(),r.title(),r.location(),r.countryCode(),WorkplaceType.UNSPECIFIED,EmploymentType.UNSPECIFIED,r.department(),r.team(),r.descriptionHtml()==null?r.descriptionPlainText():r.descriptionHtml(),r.descriptionHtml()!=null,r.applyUrl(),r.sourceUrl(),r.salaryMinimum(),r.salaryMaximum(),r.salaryCurrency(),SalaryInterval.UNSPECIFIED,r.publishedAt(),r.sourceUpdatedAt(),r.expiresAt());}
}
