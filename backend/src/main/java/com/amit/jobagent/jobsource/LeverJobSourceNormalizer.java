package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.*;
import com.amit.jobagent.jobsource.connector.RawJobRecord;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class LeverJobSourceNormalizer implements JobSourceNormalizer{
    public JobSourceType supportedType(){return JobSourceType.LEVER;}
    public JobCandidate normalize(RawJobRecord r,JobSourceRunContext s){var html=r.descriptionHtml()!=null&&!r.descriptionHtml().isBlank();return new JobCandidate(s.sourceId(),supportedType(),r.externalId(),r.company()==null?s.displayName():r.company(),r.title(),r.location(),r.countryCode(),workplace(r.workplaceType()),employment(r.employmentType()),r.department(),r.team(),html?r.descriptionHtml():r.descriptionPlainText(),html,r.applyUrl(),r.sourceUrl(),r.salaryMinimum(),r.salaryMaximum(),r.salaryCurrency(),interval(r.salaryInterval()),r.publishedAt(),r.sourceUpdatedAt(),r.expiresAt());}
    private static WorkplaceType workplace(String v){if(v==null)return WorkplaceType.UNSPECIFIED;return switch(v.toLowerCase(Locale.ROOT).replace("_","-")){case"remote"->WorkplaceType.REMOTE;case"hybrid"->WorkplaceType.HYBRID;case"on-site","onsite"->WorkplaceType.ONSITE;default->WorkplaceType.UNSPECIFIED;};}
    private static EmploymentType employment(String v){if(v==null)return EmploymentType.UNSPECIFIED;return switch(v.toLowerCase(Locale.ROOT).replace("_","-").replace(" ","-")){case"full-time","fulltime"->EmploymentType.FULL_TIME;case"part-time","parttime"->EmploymentType.PART_TIME;case"contract","contractor"->EmploymentType.CONTRACT;case"temporary"->EmploymentType.TEMPORARY;case"intern","internship"->EmploymentType.INTERNSHIP;default->EmploymentType.UNSPECIFIED;};}
    private static SalaryInterval interval(String v){if(v==null)return SalaryInterval.UNSPECIFIED;var n=v.toLowerCase(Locale.ROOT);if(n.contains("hour"))return SalaryInterval.HOUR;if(n.contains("day"))return SalaryInterval.DAY;if(n.contains("week"))return SalaryInterval.WEEK;if(n.contains("month"))return SalaryInterval.MONTH;if(n.contains("year")||n.contains("annual"))return SalaryInterval.YEAR;return SalaryInterval.OTHER;}
}
