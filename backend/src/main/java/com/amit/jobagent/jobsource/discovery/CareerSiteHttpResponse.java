package com.amit.jobagent.jobsource.discovery;

record CareerSiteHttpResponse(
        int status,
        String location,
        String contentType,
        String contentEncoding,
        byte[] body) {
    CareerSiteHttpResponse {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() { return body.clone(); }
}
