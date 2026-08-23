package com.amit.jobagent.jobsource.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProviderEndpointResolverTest {
    private final ProviderEndpointResolver resolver = new ProviderEndpointResolver();

    @Test
    void resolvesOnlyOfficialLeverGlobalAndEuHosts() {
        assertThat(resolver.leverPostings("fictional-company", SourceRegion.GLOBAL, 20, 10).toString())
                .isEqualTo("https://api.lever.co/v0/postings/fictional-company?mode=json&skip=20&limit=10");
        assertThat(resolver.leverPostings("fictional-company", SourceRegion.EU, 0, 50).getHost())
                .isEqualTo("api.eu.lever.co");
    }

    @Test
    void resolvesOnlyCurrentGreenhouseBoardApiHost() {
        assertThat(resolver.greenhouseJobs("fictional_board", SourceRegion.DEFAULT).toString())
                .isEqualTo("https://boards-api.greenhouse.io/v1/boards/fictional_board/jobs?content=true");
    }

    @Test
    void rejectsPathQueryAndUnsupportedRegionInjection() {
        for (String value : new String[] {"../private", "company/jobs", "company?url=file:///tmp", "https://evil.test"}) {
            assertThatThrownBy(() -> resolver.leverPostings(value, SourceRegion.GLOBAL, 0, 10))
                    .isInstanceOf(SourceFetchException.class)
                    .extracting(error -> ((SourceFetchException) error).code())
                    .isEqualTo(SourceFetchErrorCode.INVALID_CONFIGURATION);
        }
        for (SourceRegion region : new SourceRegion[] {SourceRegion.GLOBAL, SourceRegion.EU}) {
            assertThatThrownBy(() -> resolver.greenhouseJobs("fictional", region))
                    .isInstanceOf(SourceFetchException.class)
                    .extracting(error -> ((SourceFetchException) error).code())
                    .isEqualTo(SourceFetchErrorCode.INVALID_CONFIGURATION);
        }
    }
}
