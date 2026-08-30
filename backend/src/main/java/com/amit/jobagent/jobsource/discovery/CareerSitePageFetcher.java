package com.amit.jobagent.jobsource.discovery;

import java.net.URI;
import org.springframework.stereotype.Component;

/** Narrow public seam used by reviewed adapters; all network policy remains in the guarded client. */
@Component
public class CareerSitePageFetcher {
    private final GuardedCareerSiteClient client;

    CareerSitePageFetcher(GuardedCareerSiteClient client) { this.client = client; }

    public CareerSitePage fetch(String url) {
        CareerSiteDocument page = client.fetch(URI.create(url));
        return new CareerSitePage(page.canonicalUri().toASCIIString(), page.contentType(), page.markup());
    }
}
