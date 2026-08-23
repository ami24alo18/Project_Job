package com.amit.jobagent.job;

import com.amit.jobagent.common.error.DomainValidationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JobUrlCanonicalizer {
    private static final Set<String> TRACKING_PARAMETERS = Set.of("gclid", "fbclid", "msclkid");

    public String canonicalize(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            var uri = URI.create(value.trim());
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null)
                throw new IllegalArgumentException();
            if (uri.getRawUserInfo() != null) throw new IllegalArgumentException();
            var host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.contains(":")) host = "[" + host + "]";
            int port = uri.getPort();
            if (port == 0 || port > 65_535) throw new IllegalArgumentException();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) port = -1;
            var path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            var kept = new ArrayList<String>();
            if (uri.getRawQuery() != null) for (var part : uri.getRawQuery().split("&", -1)) {
                var rawName = part.contains("=") ? part.substring(0, part.indexOf('=')) : part;
                var name = rawName.toLowerCase(Locale.ROOT);
                if (!name.startsWith("utm_") && !TRACKING_PARAMETERS.contains(name)) kept.add(part);
            }
            var query = kept.isEmpty() ? "" : "?" + String.join("&", kept);
            return scheme + "://" + host + (port < 0 ? "" : ":" + port) + path + query;
        } catch (Exception ex) {
            throw new DomainValidationException("URL must be a valid HTTP or HTTPS URL");
        }
    }

    public String hostAndPath(String canonicalUrl) {
        if (canonicalUrl == null) return "";
        var uri = URI.create(canonicalUrl);
        return uri.getHost().toLowerCase(Locale.ROOT) + (uri.getRawPath() == null ? "/" : uri.getRawPath());
    }
}
