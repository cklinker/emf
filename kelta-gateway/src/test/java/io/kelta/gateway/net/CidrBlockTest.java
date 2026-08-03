package io.kelta.gateway.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CidrBlock Tests")
class CidrBlockTest {

    @Test
    @DisplayName("parses a well-formed IPv4 block")
    void parsesIpv4() {
        CidrBlock block = CidrBlock.parse("10.0.0.0/8");
        assertThat(block).isNotNull();
        assertThat(block.prefixLen()).isEqualTo(8);
        assertThat(block.contains("10.1.2.3")).isTrue();
        assertThat(block.contains("11.1.2.3")).isFalse();
    }

    @Test
    @DisplayName("parses a well-formed IPv6 block")
    void parsesIpv6() {
        CidrBlock block = CidrBlock.parse("2001:db8::/32");
        assertThat(block).isNotNull();
        assertThat(block.contains("2001:db8:1234::1")).isTrue();
        assertThat(block.contains("2001:db9::1")).isFalse();
    }

    @Test
    @DisplayName("handles /0 and full-length prefixes")
    void handlesEdgePrefixes() {
        assertThat(CidrBlock.parse("0.0.0.0/0").contains("203.0.113.1")).isTrue();
        CidrBlock host = CidrBlock.parse("203.0.113.7/32");
        assertThat(host.contains("203.0.113.7")).isTrue();
        assertThat(host.contains("203.0.113.8")).isFalse();
    }

    @Test
    @DisplayName("returns null for malformed input rather than throwing")
    void returnsNullForMalformed() {
        assertThat(CidrBlock.parse(null)).isNull();
        assertThat(CidrBlock.parse("")).isNull();
        assertThat(CidrBlock.parse("10.0.0.0")).isNull();       // no prefix
        assertThat(CidrBlock.parse("10.0.0.0/")).isNull();      // empty prefix
        assertThat(CidrBlock.parse("/8")).isNull();             // no network
        assertThat(CidrBlock.parse("10.0.0.0/33")).isNull();    // prefix too long for IPv4
        assertThat(CidrBlock.parse("10.0.0.0/-1")).isNull();    // negative prefix
        assertThat(CidrBlock.parse("not-an-ip/8")).isNull();
        assertThat(CidrBlock.parse("2001:db8::/129")).isNull(); // too long for IPv6
    }

    @Test
    @DisplayName("containment is false for malformed or wrong-family addresses")
    void containsRejectsBadInput() {
        CidrBlock v4 = CidrBlock.parse("10.0.0.0/8");
        assertThat(v4.contains(null)).isFalse();
        assertThat(v4.contains("nonsense")).isFalse();
        assertThat(v4.contains("2001:db8::1")).isFalse();
    }

    @Test
    @DisplayName("does not resolve hostnames (no DNS on attacker-supplied input)")
    void doesNotResolveHostnames() {
        // ofLiteral rejects hostnames outright, so a forwarded header carrying a
        // name can never trigger an outbound lookup.
        assertThat(CidrBlock.parse("localhost/8")).isNull();
        assertThat(CidrBlock.parse("10.0.0.0/8").contains("localhost")).isFalse();
    }
}
