package com.amit.jobagent.jobsource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
class WebhookTokenService {
    private final SecureRandom random = new SecureRandom();

    IssuedToken issue() {
        var bytes = new byte[32];
        random.nextBytes(bytes);
        var plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(plaintext, hash(plaintext));
    }

    boolean matches(String plaintext, String expectedHash) {
        if (plaintext == null || expectedHash == null) return false;
        return MessageDigest.isEqual(
                hash(plaintext).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    record IssuedToken(String plaintext, String hash) {}
}
