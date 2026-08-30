package com.amit.jobagent.jobsource.discovery;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

record ValidatedCareerSiteTarget(URI uri, List<InetAddress> addresses) {
    ValidatedCareerSiteTarget {
        addresses = List.copyOf(addresses);
        if (addresses.isEmpty()) throw new IllegalArgumentException("at least one pinned address is required");
    }
}
