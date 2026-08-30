package com.amit.jobagent.jobsource.discovery;

/** Safe, bounded page returned by the guarded career-site transport. */
public record CareerSitePage(String canonicalUrl, String contentType, String markup) {}
