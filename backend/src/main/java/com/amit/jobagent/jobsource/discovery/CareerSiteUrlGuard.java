package com.amit.jobagent.jobsource.discovery;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import com.amit.jobagent.common.error.DomainValidationException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class CareerSiteUrlGuard {
    private final CareerSiteDnsResolver resolver;
    private final CareerSiteDiscoveryProperties properties;

    CareerSiteUrlGuard(CareerSiteDnsResolver resolver, CareerSiteDiscoveryProperties properties) {
        this.resolver = resolver;
        this.properties = properties;
    }

    URI canonicalize(String supplied) {
        if (supplied == null || supplied.isBlank()) throw invalid("Career site URL is required");
        String value = supplied.trim();
        if (value.length() > 2_000) throw invalid("Career site URL is too long");
        if (value.indexOf('\\') >= 0 || value.chars().anyMatch(character -> Character.isISOControl(character))
                || containsEncodedProhibitedCharacter(value)) {
            throw invalid("Career site URL contains prohibited characters");
        }
        try {
            URI parsed = new URI(value);
            if (!parsed.isAbsolute() || !"https".equalsIgnoreCase(parsed.getScheme())) {
                throw invalid("Career site URL must use HTTPS");
            }
            if (parsed.getRawUserInfo() != null || parsed.getFragment() != null) {
                throw invalid("Career site URL cannot contain credentials or a fragment");
            }
            String authority = parsed.getRawAuthority();
            if (authority == null || authority.isBlank() || authority.indexOf('@') >= 0 || authority.indexOf('%') >= 0) {
                throw invalid("Career site URL must contain an unambiguous hostname");
            }
            if (authority.startsWith("[") || authority.indexOf(']') >= 0) {
                throw invalid("Literal IP addresses are not allowed");
            }
            int port = -1;
            String hostPart = authority;
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                if (authority.indexOf(':') != colon) throw invalid("Literal IP addresses are not allowed");
                hostPart = authority.substring(0, colon);
                String rawPort = authority.substring(colon + 1);
                try {
                    port = Integer.parseInt(rawPort);
                } catch (NumberFormatException ex) {
                    throw invalid("Career site URL contains an invalid port");
                }
            }
            String host = IDN.toASCII(hostPart, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (host.isBlank() || host.endsWith(".") || !host.contains(".") || host.equals("localhost")
                    || host.matches("[0-9.]+") || host.matches("(?i)0x[0-9a-f]+")) {
                throw invalid("Career site URL must use a public DNS hostname, not a literal or local address");
            }
            int effectivePort = port == -1 ? 443 : port;
            if (!properties.allowedPorts().contains(effectivePort)) {
                throw invalid("Career site URL uses a port that is not allowed");
            }
            int canonicalPort = effectivePort == 443 ? -1 : effectivePort;
            String path = parsed.getPath();
            if (path == null || path.isEmpty()) path = "/";
            return new URI("https", null, host, canonicalPort, path, parsed.getQuery(), null).normalize();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            throw invalid("Career site URL is invalid");
        }
    }

    ValidatedCareerSiteTarget validateAndResolve(URI canonicalUri) {
        URI uri = canonicalize(canonicalUri.toASCIIString());
        final List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(uri.getHost());
        } catch (UnknownHostException ex) {
            throw new CareerSiteDiscoveryException(HttpStatus.UNPROCESSABLE_ENTITY, "DNS_RESOLUTION_FAILED",
                    "The career site hostname could not be resolved", ex);
        }
        if (addresses == null || addresses.isEmpty()) {
            throw new CareerSiteDiscoveryException(HttpStatus.UNPROCESSABLE_ENTITY, "DNS_RESOLUTION_FAILED",
                    "The career site hostname did not resolve to a public address");
        }
        if (addresses.stream().anyMatch(address -> !isPublic(address))) {
            throw invalid("Career site hostname resolves to a prohibited network address");
        }
        return new ValidatedCareerSiteTarget(uri, addresses);
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address || bytes.length == 4) return isPublicIpv4(bytes, 0);
        if (bytes.length != 16) return false;
        boolean mapped = true;
        for (int index = 0; index < 10; index++) mapped &= bytes[index] == 0;
        if (mapped && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) return isPublicIpv4(bytes, 12);
        int first = unsigned(bytes[0]);
        if ((first & 0xe0) != 0x20) return false; // Only IPv6 global-unicast space (2000::/3).
        return !(unsigned(bytes[0]) == 0x20 && unsigned(bytes[1]) == 0x01
                && unsigned(bytes[2]) == 0x0d && unsigned(bytes[3]) == 0xb8); // Documentation range.
    }

    private static boolean isPublicIpv4(byte[] bytes, int offset) {
        int a = unsigned(bytes[offset]);
        int b = unsigned(bytes[offset + 1]);
        int c = unsigned(bytes[offset + 2]);
        int d = unsigned(bytes[offset + 3]);
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && b >= 64 && b <= 127) return false;
        if (a == 169 && b == 254) return false;
        if (a == 172 && b >= 16 && b <= 31) return false;
        if (a == 192 && b == 168) return false;
        if (a == 192 && b == 0 && c == 0) return false;
        if (a == 192 && b == 0 && c == 2) return false;
        if (a == 192 && b == 88 && c == 99) return false;
        if (a == 198 && (b == 18 || b == 19)) return false;
        if (a == 198 && b == 51 && c == 100) return false;
        if (a == 203 && b == 0 && c == 113) return false;
        return !(a == 255 && b == 255 && c == 255 && d == 255);
    }

    private static int unsigned(byte value) { return value & 0xff; }
    private static boolean containsEncodedProhibitedCharacter(String value) {
        for (int index = 0; index + 2 < value.length(); index++) {
            if (value.charAt(index) != '%') continue;
            int high = Character.digit(value.charAt(index + 1), 16);
            int low = Character.digit(value.charAt(index + 2), 16);
            if (high < 0 || low < 0) continue;
            int decoded = high * 16 + low;
            if (decoded <= 0x20 || decoded == 0x7f || decoded == '\\') return true;
        }
        return false;
    }
    private static DomainValidationException invalid(String message) { return new DomainValidationException(message); }
}
