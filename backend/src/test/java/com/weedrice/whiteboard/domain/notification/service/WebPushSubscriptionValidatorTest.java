package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebPushSubscriptionValidatorTest {

    @Mock
    private WebPushHostResolver hostResolver;

    private WebPushSubscriptionValidator validator;

    @BeforeEach
    void setUp() {
        WebPushProperties properties = new WebPushProperties();
        properties.setAllowedHosts(List.of("push.example", ".notify.example"));
        validator = new WebPushSubscriptionValidator(properties, hostResolver);
    }

    @Test
    void registrationAcceptsExactAndConfiguredSubdomainProviders() {
        assertThatCode(() -> validator.validateForRegistration(
                "https://PUSH.EXAMPLE/subscription", validP256dh(), validAuth())).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateForRegistration(
                "https://region.notify.example/push", validP256dh(), validAuth())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://push.example/subscription",
            "https://user@push.example/subscription",
            "https://push.example/subscription#fragment",
            "https://push.example:8443/subscription",
            "https://127.0.0.1/subscription",
            "https://[::1]/subscription",
            "https://attacker.example/subscription"
    })
    void registrationRejectsUnsafeEndpoint(String endpoint) {
        assertThatThrownBy(() -> validator.validateForRegistration(endpoint, validP256dh(), validAuth()))
                .isInstanceOf(InvalidWebPushSubscriptionException.class);
    }

    @Test
    void registrationRejectsMalformedOrInvalidKeyMaterial() {
        assertThatThrownBy(() -> validator.validateForRegistration(
                "https://push.example/subscription", "not+a+base64url/key", validAuth()))
                .isInstanceOf(InvalidWebPushSubscriptionException.class);
        assertThatThrownBy(() -> validator.validateForRegistration(
                "https://push.example/subscription", validP256dh(), "short"))
                .isInstanceOf(InvalidWebPushSubscriptionException.class);

        byte[] offCurve = Base64.getUrlDecoder().decode(validP256dh());
        offCurve[64] ^= 1;
        assertThatThrownBy(() -> validator.validateForRegistration(
                "https://push.example/subscription",
                Base64.getUrlEncoder().withoutPadding().encodeToString(offCurve),
                validAuth()))
                .isInstanceOf(InvalidWebPushSubscriptionException.class);
    }

    @Test
    void deliveryAcceptsOnlyPublicResolvedAddresses() throws Exception {
        PushSubscriptionSnapshot snapshot = snapshot("https://push.example/subscription");
        when(hostResolver.resolve("push.example"))
                .thenReturn(new InetAddress[] {InetAddress.getByAddress(new byte[] {8, 8, 8, 8})});

        assertThatCode(() -> validator.validateForDelivery(snapshot)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.0.0.1", "100.64.0.1", "169.254.1.1", "192.0.2.1", "::1", "fc00::1", "2001:db8::1"})
    void deliveryRejectsPrivateAndReservedResolvedAddresses(String address) throws Exception {
        PushSubscriptionSnapshot snapshot = snapshot("https://push.example/subscription");
        when(hostResolver.resolve("push.example"))
                .thenReturn(new InetAddress[] {InetAddress.getByName(address)});

        assertThatThrownBy(() -> validator.validateForDelivery(snapshot))
                .isInstanceOf(InvalidWebPushSubscriptionException.class);
    }

    @Test
    void deliveryKeepsDnsFailureRetryable() throws Exception {
        PushSubscriptionSnapshot snapshot = snapshot("https://push.example/subscription");
        when(hostResolver.resolve("push.example")).thenThrow(new UnknownHostException("push.example"));

        assertThatThrownBy(() -> validator.validateForDelivery(snapshot))
                .isInstanceOf(UnknownHostException.class);
    }

    private PushSubscriptionSnapshot snapshot(String endpoint) {
        return new PushSubscriptionSnapshot(
                1L, 2L, endpoint, validP256dh(), validAuth(), LocalDateTime.of(2026, 7, 18, 12, 0));
    }

    private static String validP256dh() {
        byte[] publicKey = HexFormat.of().parseHex(
                "046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"
                        + "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey);
    }

    private static String validAuth() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
    }
}
