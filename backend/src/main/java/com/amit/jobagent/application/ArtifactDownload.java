package com.amit.jobagent.application;

record ArtifactDownload(String fileName, String contentType, byte[] bytes) {
    ArtifactDownload {
        bytes = bytes.clone();
    }

    @Override public byte[] bytes() { return bytes.clone(); }
}
