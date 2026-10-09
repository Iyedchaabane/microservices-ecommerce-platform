package com.ichaabane.gateway.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client identification for rate-limit bucketing. The default must never trust
 * client-supplied forwarding headers; forwarded information is honoured only
 * for explicitly trusted proxies.
 */
class ClientIpResolverTest {

    private static final String PEER = "127.0.0.1";

    private ClientIpResolver resolver(boolean trust, Set<String> trusted, boolean exemptLoopback) {
        return new ClientIpResolver(trust, trusted, exemptLoopback);
    }

    /** Builds an exchange from {@code peer} carrying an optional X-Forwarded-For. */
    private static ServerWebExchange exchange(String peer, String... xff) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.get("/api/v1/products");
        if (peer != null) {
            builder.remoteAddress(new InetSocketAddress(peer, 51234));
        }
        if (xff.length > 0) {
            builder.header("X-Forwarded-For", xff);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    @DisplayName("Default: X-Forwarded-For is ignored (spoofed header cannot change the bucket)")
    void spoofedHeaderIsIgnoredByDefault() {
        ClientIpResolver resolver = resolver(false, Set.of(), false);

        assertThat(resolver.resolve(exchange(PEER, "203.0.113.7"))).isEqualTo(PEER);
        assertThat(resolver.resolve(exchange(PEER, "198.51.100.9"))).isEqualTo(PEER);
    }

    @Test
    @DisplayName("Trust enabled but peer not trusted: forwarded header is still ignored")
    void untrustedPeerCannotInjectForwardedIp() {
        ClientIpResolver resolver = resolver(true, Set.of("10.0.0.1"), false);

        assertThat(resolver.resolve(exchange("203.0.113.50", "1.2.3.4")))
                .isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("Trusted peer: the forwarded client address is used")
    void trustedProxyUsesForwardedClient() {
        ClientIpResolver resolver = resolver(true, Set.of("10.0.0.1"), false);

        assertThat(resolver.resolve(exchange("10.0.0.1", "203.0.113.7")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Trusted chain: rightmost untrusted hop is the client")
    void trustedChainUsesRightmostUntrustedHop() {
        ClientIpResolver resolver = resolver(true, Set.of("10.0.0.1", "10.0.0.2"), false);

        assertThat(resolver.resolve(exchange("10.0.0.2", "203.0.113.7, 10.0.0.1")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("All forwarded hops trusted: falls back to the peer address")
    void allTrustedHopsFallBackToPeer() {
        ClientIpResolver resolver = resolver(true, Set.of("10.0.0.1", "10.0.0.2"), false);

        assertThat(resolver.resolve(exchange("10.0.0.2", "10.0.0.1")))
                .isEqualTo("10.0.0.2");
    }

    @Test
    @DisplayName("Forwarded host with a port has the port stripped")
    void forwardedHostPortIsStripped() {
        ClientIpResolver resolver = resolver(true, Set.of("10.0.0.1"), false);

        assertThat(resolver.resolve(exchange("10.0.0.1", "203.0.113.7:51234")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Different peers resolve to different keys")
    void differentPeersAreIndependent() {
        ClientIpResolver resolver = resolver(false, Set.of(), false);

        assertThat(resolver.resolve(exchange("192.0.2.1"))).isEqualTo("192.0.2.1");
        assertThat(resolver.resolve(exchange("192.0.2.2"))).isEqualTo("192.0.2.2");
    }

    @Test
    @DisplayName("Missing remote address resolves to a stable placeholder")
    void missingRemoteAddressIsUnknown() {
        ClientIpResolver resolver = resolver(false, Set.of(), false);

        assertThat(resolver.resolve(exchange(null))).isEqualTo("unknown");
    }

    @Test
    @DisplayName("Multiple X-Forwarded-For headers: walked right-to-left across all headers")
    void multipleXffHeadersWalkedRightToLeft() {
        ClientIpResolver resolver = resolver(true, Set.of("10.0.0.1", "10.0.0.2"), false);

        // Header 0 has spoofed IP; Header 1 has real client appended by proxy 10.0.0.1
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/products")
                .remoteAddress(new InetSocketAddress("10.0.0.2", 51234))
                .header("X-Forwarded-For", "198.51.100.99") // attacker-injected header 0
                .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1") // legitimate proxy-appended header 1
                .build();

        assertThat(resolver.resolve(MockServerWebExchange.from(request)))
                .as("must extract 203.0.113.7 from the latest header, not the spoofed 198.51.100.99 from the first header")
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("Quoted forwarding tokens are stripped of quotes")
    void quotedForwardingTokensAreStripped() {
        ClientIpResolver resolver = resolver(true, Set.of("10.0.0.1"), false);

        assertThat(resolver.resolve(exchange("10.0.0.1", "\"203.0.113.7\"")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("IPv6 with port and zone index is handled safely")
    void ipv6WithPortAndZoneIndexIsHandled() {
        ClientIpResolver resolver = resolver(true, Set.of("10.0.0.1"), false);

        assertThat(resolver.resolve(exchange("10.0.0.1", "[2001:db8::1]:8080")))
                .isEqualTo("2001:db8::1");
        assertThat(resolver.resolve(exchange("10.0.0.1", "fe80::1%eth0")))
                .isEqualTo("fe80::1");
    }

    @Test
    @DisplayName("Loopback exemption is opt-in")
    void loopbackExemptionIsOptIn() {
        assertThat(resolver(false, Set.of(), false).isExempt(exchange(PEER)))
                .as("default: loopback traffic is still limited")
                .isFalse();

        assertThat(resolver(false, Set.of(), true).isExempt(exchange(PEER)))
                .as("opt-in: loopback traffic is exempt")
                .isTrue();

        assertThat(resolver(false, Set.of(), true).isExempt(exchange("203.0.113.9")))
                .as("non-loopback traffic is never exempt")
                .isFalse();
    }
}
