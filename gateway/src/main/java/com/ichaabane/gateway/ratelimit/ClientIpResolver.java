package com.ichaabane.gateway.ratelimit;

import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;

/**
 * Resolves the client identity used for rate-limit bucketing.
 *
 * <p><b>Safe by default.</b> The key is the TCP peer address unless the request
 * provably arrives from a configured trusted proxy AND forwarded-header trust
 * is enabled. {@code X-Forwarded-For} is fully client-controlled, so honouring
 * it unconditionally would let a caller rotate buckets by editing a header and
 * bypass the limit entirely.</p>
 *
 * <p><b>Trusted-proxy mode.</b> When enabled and the immediate peer is listed
 * in {@code rate-limit.trusted-proxies}, the resolver walks {@code X-Forwarded-For}
 * right-to-left, skipping hops that are themselves trusted proxies, and returns
 * the first untrusted address — the closest hop the trusted infrastructure
 * vouches for. If nothing untrusted is present it falls back to the peer.</p>
 *
 * <p><b>Loopback.</b> Internal services in this platform call the gateway over
 * loopback ({@code http://localhost:8222}). By default those calls share the
 * loopback bucket; set {@code rate-limit.exempt-loopback=true} to skip limiting
 * for loopback peers. It is opt-in because the default deployment also reaches
 * the gateway over loopback (curl / local clients), where exempting it would
 * silently disable the limiter.</p>
 */
public final class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    private final boolean trustForwardedHeaders;
    private final Set<String> trustedProxies;
    private final boolean exemptLoopback;

    public ClientIpResolver(boolean trustForwardedHeaders, Set<String> trustedProxies,
                            boolean exemptLoopback) {
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.trustedProxies = Set.copyOf(trustedProxies);
        this.exemptLoopback = exemptLoopback;
    }

    /** True when the request originates from loopback and loopback is exempt. */
    public boolean isExempt(ServerWebExchange exchange) {
        if (!exemptLoopback) {
            return false;
        }
        InetAddress address = remoteAddress(exchange);
        return address != null && address.isLoopbackAddress();
    }

    /** The bucket key for a request. Never returns null. */
    public String resolve(ServerWebExchange exchange) {
        InetAddress address = remoteAddress(exchange);
        String peerIp = address != null ? address.getHostAddress() : UNKNOWN;

        if (!trustForwardedHeaders || !trustedProxies.contains(peerIp)) {
            return peerIp;
        }
        List<String> forwarded = exchange.getRequest().getHeaders().get("X-Forwarded-For");
        if (forwarded == null || forwarded.isEmpty()) {
            return peerIp;
        }
        // Rightmost untrusted hop across all headers is the closest address a trusted proxy attests.
        for (int h = forwarded.size() - 1; h >= 0; h--) {
            String header = forwarded.get(h);
            String[] hops = header.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = stripPort(hops[i].trim());
                if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
                    return hop;
                }
            }
        }
        return peerIp;
    }

    private static InetAddress remoteAddress(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null ? remote.getAddress() : null;
    }

    /** Removes an optional port and quotes from a host token ({@code 1.2.3.4:56}, {@code [::1]:56}, {@code "1.2.3.4"}). */
    private static String stripPort(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() > 1) {
            token = token.substring(1, token.length() - 1).trim();
        }
        if (token.startsWith("[")) {
            int end = token.indexOf(']');
            return end > 0 ? token.substring(1, end) : token;
        }
        int firstColon = token.indexOf(':');
        // A single colon means host:port; multiple colons mean a bare IPv6 literal.
        if (firstColon > 0 && token.indexOf(':', firstColon + 1) < 0) {
            token = token.substring(0, firstColon);
        }
        int zoneIndex = token.indexOf('%');
        if (zoneIndex > 0) {
            token = token.substring(0, zoneIndex);
        }
        return token;
    }
}
