package com.amit.jobagent.jobsource.discovery;

import com.amit.jobagent.common.error.CareerSiteDiscoveryException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Minimal HTTP/1.1 transport that opens sockets only to DNS addresses approved by the guard. */
@Component
class PinnedHttpsTransport implements CareerSiteHttpTransport {
    private static final String USER_AGENT = "job-application-agent/0.4 (career-site-discovery)";
    private static final int MAXIMUM_HEADER_BYTES = 64 * 1024;
    private static final int MAXIMUM_LINE_BYTES = 8 * 1024;
    private final CareerSiteDiscoveryProperties properties;
    private final SSLSocketFactory tlsFactory;

    @Autowired
    PinnedHttpsTransport(CareerSiteDiscoveryProperties properties) {
        this(properties, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    PinnedHttpsTransport(CareerSiteDiscoveryProperties properties, SSLSocketFactory tlsFactory) {
        this.properties = properties;
        this.tlsFactory = tlsFactory;
    }

    @Override
    public CareerSiteHttpResponse get(ValidatedCareerSiteTarget target) {
        try (SSLSocket socket = connect(target)) {
            writeRequest(socket, target);
            return readResponse(socket.getInputStream());
        } catch (CareerSiteDiscoveryException ex) {
            throw ex;
        } catch (SocketTimeoutException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "TIMEOUT",
                    "The career site did not respond before the configured timeout", ex);
        } catch (SSLException ex) {
            throw failure(HttpStatus.UNPROCESSABLE_ENTITY, "TLS_VALIDATION_FAILED",
                    "The career site TLS identity could not be validated", ex);
        } catch (IOException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "REMOTE_IO_ERROR",
                    "The career site could not be reached", ex);
        }
    }

    private SSLSocket connect(ValidatedCareerSiteTarget target) throws IOException {
        String host = target.uri().getHost();
        int port = target.uri().getPort() == -1 ? 443 : target.uri().getPort();
        IOException last = null;
        for (InetAddress address : target.addresses()) {
            Socket plain = new Socket(Proxy.NO_PROXY);
            try {
                plain.connect(new InetSocketAddress(address, port), properties.connectTimeoutMillis());
                plain.setSoTimeout(properties.requestTimeoutMillis());
                SSLSocket tls = (SSLSocket) tlsFactory.createSocket(plain, host, port, true);
                var parameters = tls.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(List.of(new SNIHostName(host)));
                tls.setSSLParameters(parameters);
                tls.setSoTimeout(properties.requestTimeoutMillis());
                tls.startHandshake();
                return tls;
            } catch (IOException ex) {
                last = ex;
                try { plain.close(); } catch (IOException ignored) { }
            }
        }
        throw last == null ? new IOException("no validated career-site address was available") : last;
    }

    private static void writeRequest(SSLSocket socket, ValidatedCareerSiteTarget target) throws IOException {
        String path = target.uri().getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (target.uri().getRawQuery() != null) path += '?' + target.uri().getRawQuery();
        String host = target.uri().getHost();
        int port = target.uri().getPort();
        String hostHeader = port == -1 || port == 443 ? host : host + ':' + port;
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Accept: text/html,application/xhtml+xml;q=0.9\r\n"
                + "Accept-Encoding: gzip, identity;q=0.8\r\n"
                + "User-Agent: " + USER_AGENT + "\r\n"
                + "Connection: close\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    CareerSiteHttpResponse readResponse(InputStream rawInput) throws IOException {
        var input = new BufferedInputStream(rawInput);
        HeaderBudget budget = new HeaderBudget();
        String statusLine = readLine(input, budget);
        if (statusLine == null || !statusLine.matches("HTTP/1\\.[01] [0-9]{3}(?: .*)?")) {
            throw invalidResponse("The career site returned an invalid HTTP response");
        }
        int status = Integer.parseInt(statusLine.substring(9, 12));
        String location = null;
        String contentType = null;
        String contentEncoding = null;
        String transferEncoding = null;
        Long contentLength = null;
        String line;
        while ((line = readLine(input, budget)) != null && !line.isEmpty()) {
            if (line.startsWith(" ") || line.startsWith("\t")) throw invalidResponse("The career site returned invalid HTTP headers");
            int colon = line.indexOf(':');
            if (colon <= 0) throw invalidResponse("The career site returned invalid HTTP headers");
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (name) {
                case "location" -> location = single(location, value);
                case "content-type" -> contentType = single(contentType, value);
                case "content-encoding" -> contentEncoding = single(contentEncoding, value);
                case "transfer-encoding" -> transferEncoding = single(transferEncoding, value);
                case "content-length" -> contentLength = parseLength(contentLength, value);
                default -> { }
            }
        }
        if (line == null) throw invalidResponse("The career site response ended before its headers were complete");
        if (isRedirect(status) || status < 200 || status > 299 || status == 204) {
            return new CareerSiteHttpResponse(status, location, contentType, contentEncoding, new byte[0]);
        }
        byte[] body = readBody(input, transferEncoding, contentLength);
        return new CareerSiteHttpResponse(status, location, contentType, contentEncoding, body);
    }

    private byte[] readBody(InputStream input, String transferEncoding, Long contentLength) throws IOException {
        if (transferEncoding != null && !transferEncoding.equalsIgnoreCase("identity")) {
            if (!transferEncoding.equalsIgnoreCase("chunked")) {
                throw invalidResponse("The career site returned an unsupported transfer encoding");
            }
            return readChunked(input);
        }
        if (contentLength != null) {
            if (contentLength > properties.maximumCompressedBytes()) throw tooLarge();
            return readExactly(input, contentLength.intValue());
        }
        return readUntilClose(input);
    }

    private byte[] readChunked(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        HeaderBudget trailerBudget = new HeaderBudget();
        while (true) {
            String chunkLine = readLine(input, trailerBudget);
            if (chunkLine == null) throw new EOFException("chunk size was missing");
            int extension = chunkLine.indexOf(';');
            String sizeValue = (extension >= 0 ? chunkLine.substring(0, extension) : chunkLine).trim();
            if (sizeValue.isEmpty() || sizeValue.length() > 16 || !sizeValue.matches("[0-9A-Fa-f]+")) {
                throw invalidResponse("The career site returned invalid chunked content");
            }
            long chunkSize;
            try {
                chunkSize = Long.parseLong(sizeValue, 16);
            } catch (NumberFormatException ex) {
                throw invalidResponse("The career site returned invalid chunked content");
            }
            if (chunkSize == 0) {
                String trailer;
                while ((trailer = readLine(input, trailerBudget)) != null && !trailer.isEmpty()) { }
                if (trailer == null) throw invalidResponse("The career site returned incomplete chunk trailers");
                return output.toByteArray();
            }
            if (chunkSize > properties.maximumCompressedBytes()
                    || (long) output.size() + chunkSize > properties.maximumCompressedBytes()) throw tooLarge();
            output.write(readExactly(input, (int) chunkSize));
            if (input.read() != '\r' || input.read() != '\n') throw invalidResponse("The career site returned invalid chunk framing");
        }
    }

    private byte[] readUntilClose(InputStream input) throws IOException {
        byte[] body = input.readNBytes(properties.maximumCompressedBytes() + 1);
        if (body.length > properties.maximumCompressedBytes()) throw tooLarge();
        return body;
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] body = input.readNBytes(length);
        if (body.length != length) throw new EOFException("response body ended early");
        return body;
    }

    private static String readLine(InputStream input, HeaderBudget budget) throws IOException {
        var line = new ByteArrayOutputStream();
        boolean carriageReturn = false;
        while (true) {
            int value = input.read();
            if (value == -1) return line.size() == 0 && !carriageReturn ? null : failIncompleteLine();
            budget.add(1);
            if (carriageReturn) {
                if (value != '\n') throw invalidResponse("The career site returned invalid HTTP line endings");
                return line.toString(StandardCharsets.ISO_8859_1);
            }
            if (value == '\r') {
                carriageReturn = true;
            } else {
                if (value == '\n' || line.size() >= MAXIMUM_LINE_BYTES) {
                    throw invalidResponse("The career site returned an oversized or invalid HTTP header line");
                }
                line.write(value);
            }
        }
    }

    private static String failIncompleteLine() throws IOException { throw new EOFException("HTTP line ended early"); }

    private static String single(String existing, String value) {
        if (existing != null && !existing.equals(value)) throw invalidResponse("The career site returned conflicting HTTP headers");
        return value;
    }

    private static Long parseLength(Long existing, String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0 || (existing != null && existing != parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException ex) {
            throw invalidResponse("The career site returned an invalid content length");
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static CareerSiteDiscoveryException tooLarge() {
        return failure(HttpStatus.UNPROCESSABLE_ENTITY, "RESPONSE_TOO_LARGE",
                "The career site response exceeds the configured size limit", null);
    }

    private static CareerSiteDiscoveryException invalidResponse(String message) {
        return failure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_HTTP_RESPONSE", message, null);
    }

    private static CareerSiteDiscoveryException failure(HttpStatus status, String code, String message, Throwable cause) {
        return cause == null ? new CareerSiteDiscoveryException(status, code, message)
                : new CareerSiteDiscoveryException(status, code, message, cause);
    }

    private static final class HeaderBudget {
        private int consumed;
        private void add(int count) {
            consumed += count;
            if (consumed > MAXIMUM_HEADER_BYTES) throw invalidResponse("The career site returned oversized HTTP headers");
        }
    }
}
