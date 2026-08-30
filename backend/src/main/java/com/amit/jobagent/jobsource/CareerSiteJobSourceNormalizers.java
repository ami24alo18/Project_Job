package com.amit.jobagent.jobsource;

import com.amit.jobagent.job.EmploymentType;
import com.amit.jobagent.job.JobCandidate;
import com.amit.jobagent.job.JobIngestionProvider;
import com.amit.jobagent.job.JobSourceType;
import com.amit.jobagent.job.SalaryInterval;
import com.amit.jobagent.job.WorkplaceType;
import com.amit.jobagent.jobsource.connector.RawJobRecord;
import java.util.Locale;
import org.springframework.stereotype.Component;

abstract class CareerSiteJobSourceNormalizer implements JobSourceNormalizer {
    @Override public JobSourceType supportedType() { return JobSourceType.CAREER_SITE; }

    @Override
    public JobCandidate normalize(RawJobRecord raw, JobSourceRunContext source) {
        boolean html = raw.descriptionHtml() != null && !raw.descriptionHtml().isBlank();
        return new JobCandidate(source.sourceId(), JobSourceType.CAREER_SITE, raw.externalId(),
                raw.company() == null ? source.displayName() : raw.company(), raw.title(), raw.location(),
                raw.countryCode(), workplace(raw.workplaceType()), employment(raw.employmentType()),
                raw.department(), raw.team(), html ? raw.descriptionHtml() : raw.descriptionPlainText(), html,
                raw.applyUrl(), raw.sourceUrl(), raw.salaryMinimum(), raw.salaryMaximum(), raw.salaryCurrency(),
                SalaryInterval.UNSPECIFIED, raw.publishedAt(), raw.sourceUpdatedAt(), raw.expiresAt(),
                JobIngestionProvider.valueOf(source.connectorType().name()), source.canonicalHost(), null,
                null, null);
    }

    private static WorkplaceType workplace(String value) {
        String normalized = normalized(value);
        if (normalized.contains("REMOTE")) return WorkplaceType.REMOTE;
        if (normalized.contains("HYBRID")) return WorkplaceType.HYBRID;
        if (normalized.contains("ONSITE") || normalized.contains("ON_SITE")) return WorkplaceType.ONSITE;
        return WorkplaceType.UNSPECIFIED;
    }

    private static EmploymentType employment(String value) {
        String normalized = normalized(value);
        if (normalized.contains("FULL")) return EmploymentType.FULL_TIME;
        if (normalized.contains("PART")) return EmploymentType.PART_TIME;
        if (normalized.contains("CONTRACT")) return EmploymentType.CONTRACT;
        if (normalized.contains("INTERN")) return EmploymentType.INTERNSHIP;
        if (normalized.contains("TEMP")) return EmploymentType.TEMPORARY;
        return EmploymentType.UNSPECIFIED;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}

@Component
final class SmartRecruitersJobSourceNormalizer extends CareerSiteJobSourceNormalizer {
    @Override public JobSourceConnectorType supportedConnector() { return JobSourceConnectorType.SMARTRECRUITERS; }
}

@Component
final class GenericJsonLdJobSourceNormalizer extends CareerSiteJobSourceNormalizer {
    @Override public JobSourceConnectorType supportedConnector() { return JobSourceConnectorType.GENERIC_JSON_LD; }
}
