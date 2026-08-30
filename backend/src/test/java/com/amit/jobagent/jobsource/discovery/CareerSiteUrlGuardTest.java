package com.amit.jobagent.jobsource.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amit.jobagent.common.error.DomainValidationException;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CareerSiteUrlGuardTest {
    @Test
    void canonicalizesIdnHostDefaultPortAndPath() throws Exception {
        var guard = guard(List.of(InetAddress.getByName("93.184.216.34")));

        URI result = guard.canonicalize("  https://BÜCHER.example:443/a/../careers  ");

        assertThat(result.toASCIIString()).isEqualTo("https://xn--bcher-kva.example/careers");
    }

    @ParameterizedTest
    @MethodSource("unsafeUrls")
    void rejectsAmbiguousOrUnsafeUrlsBeforeDns(String url) throws Exception {
        var guard = guard(List.of(InetAddress.getByName("93.184.216.34")));

        assertThatThrownBy(() -> guard.canonicalize(url))
                .isInstanceOf(DomainValidationException.class);
    }

    static Stream<String> unsafeUrls() {
        return Stream.of(
                "http://careers.example.com/jobs",
                "https://user:secret@careers.example.com/jobs",
                "https://careers.example.com/jobs#section",
                "https://127.0.0.1/jobs",
                "https://[::1]/jobs",
                "https://2130706433/jobs",
                "https://0177.0.0.1/jobs",
                "https://0x7f000001/jobs",
                "https://localhost/jobs",
                "https://careers.example.com:8443/jobs",
                "https://careers.example.com\\@public.example/jobs",
                "https://careers.example.com/%0a/jobs");
    }

    @ParameterizedTest
    @MethodSource("prohibitedAddresses")
    void rejectsEveryProhibitedDnsAnswer(String address) throws Exception {
        var guard = guard(List.of(InetAddress.getByName(address)));

        assertThatThrownBy(() -> guard.validateAndResolve(URI.create("https://careers.example.com/jobs")))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("prohibited network address");
    }

    static Stream<String> prohibitedAddresses() {
        return Stream.of(
                "0.0.0.0", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.169.254",
                "172.16.0.1", "192.0.0.10", "192.0.2.1", "192.168.1.1", "198.18.0.1",
                "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1",
                "::", "::1", "fc00::1", "fe80::1", "ff02::1", "2001:db8::1", "::ffff:127.0.0.1");
    }

    @Test
    void rejectsHostWhenOnlyOneOfSeveralAnswersIsPrivate() throws Exception {
        var guard = guard(List.of(
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("10.0.0.5")));

        assertThatThrownBy(() -> guard.validateAndResolve(URI.create("https://careers.example.com/jobs")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void acceptsPublicIpv4AndIpv6Answers() throws Exception {
        var guard = guard(List.of(
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("2606:4700:4700::1111")));

        var result = guard.validateAndResolve(URI.create("https://careers.example.com/jobs"));

        assertThat(result.addresses()).hasSize(2);
        assertThat(result.uri().getHost()).isEqualTo("careers.example.com");
    }

    private static CareerSiteUrlGuard guard(List<InetAddress> addresses) {
        return new CareerSiteUrlGuard(host -> addresses, CareerSiteDiscoveryTestSupport.properties());
    }
}
