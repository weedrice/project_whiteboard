package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpAddressCanonicalizerTest {

    @Test
    @DisplayName("IPv4 주소의 공백과 leading zero를 정규화한다")
    void canonicalize_ipv4_trimsAndRemovesLeadingZeros() {
        assertThat(IpAddressCanonicalizer.canonicalize(" 010.000.000.001 "))
                .contains("10.0.0.1");
    }

    @Test
    @DisplayName("IPv6 주소를 소문자 압축 표현으로 정규화한다")
    void canonicalize_ipv6_compressesAndLowercases() {
        assertThat(IpAddressCanonicalizer.canonicalize("2001:DB8:0:0:0:0:0:1"))
                .contains("2001:db8::1");
    }

    @Test
    @DisplayName("유효하지 않은 IP 주소는 empty를 반환한다")
    void canonicalize_invalid_returnsEmpty() {
        assertThat(IpAddressCanonicalizer.canonicalize("127.0.0.1/32")).isEmpty();
        assertThat(IpAddressCanonicalizer.canonicalize("localhost")).isEmpty();
        assertThat(IpAddressCanonicalizer.canonicalize("2001:db8::1%eth0")).isEmpty();
    }
}
