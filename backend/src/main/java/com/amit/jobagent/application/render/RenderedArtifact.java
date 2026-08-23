package com.amit.jobagent.application.render;

import com.amit.jobagent.application.ArtifactType;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/** Complete transient artifact data ready for private object-storage persistence. */
public record RenderedArtifact(
        ArtifactType artifactType,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256Checksum,
        String templateVersion,
        byte[] bytes) {

    public RenderedArtifact {
        artifactType = Objects.requireNonNull(artifactType, "artifactType is required");
        var format = ArtifactFormat.forType(artifactType);
        if (fileName == null || fileName.isBlank()
                || !fileName.toLowerCase(Locale.ROOT).endsWith("." + format.extension)) {
            throw new IllegalArgumentException("fileName must use the artifact extension");
        }
        if (!format.contentType.equals(contentType)) {
            throw new IllegalArgumentException("contentType does not match artifactType");
        }
        if (templateVersion == null || templateVersion.isBlank()) {
            throw new IllegalArgumentException("templateVersion is required");
        }
        bytes = Objects.requireNonNull(bytes, "bytes are required").clone();
        if (bytes.length == 0 || sizeBytes != bytes.length) {
            throw new IllegalArgumentException("sizeBytes must match non-empty artifact bytes");
        }
        String calculatedChecksum = sha256(bytes);
        if (!calculatedChecksum.equals(sha256Checksum)) {
            throw new IllegalArgumentException("sha256Checksum does not match artifact bytes");
        }
    }

    public static RenderedArtifact create(
            ArtifactType artifactType,
            String fileName,
            String templateVersion,
            byte[] bytes) {
        var format = ArtifactFormat.forType(artifactType);
        return new RenderedArtifact(
                artifactType,
                fileName,
                format.contentType,
                bytes.length,
                sha256(bytes),
                templateVersion,
                bytes);
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
