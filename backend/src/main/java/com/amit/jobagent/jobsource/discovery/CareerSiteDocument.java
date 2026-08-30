package com.amit.jobagent.jobsource.discovery;

import java.net.URI;

record CareerSiteDocument(URI canonicalUri, String contentType, String markup) {}
