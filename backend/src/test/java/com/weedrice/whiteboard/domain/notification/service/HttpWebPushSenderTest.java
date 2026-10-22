package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.message.BasicHttpResponse;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpWebPushSenderTest {

    @Test
    void preparesEncryptedSignedPushAndSendsThroughApacheClient() throws Exception {
        boolean providerPreviouslyInstalled = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", new BouncyCastleProvider());
            generator.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
            KeyPair vapid = generator.generateKeyPair();
            KeyPair subscriber = generator.generateKeyPair();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

            WebPushProperties properties = new WebPushProperties();
            properties.setPublicKey(encoder.encodeToString(Utils.encode((ECPublicKey) vapid.getPublic())));
            properties.setPrivateKey(encoder.encodeToString(Utils.encode((ECPrivateKey) vapid.getPrivate())));
            properties.setSubject("mailto:test@example.invalid");
            PushSubscriptionSnapshot subscription = new PushSubscriptionSnapshot(
                    1L, 2L, "https://push.example/subscription",
                    encoder.encodeToString(Utils.encode((ECPublicKey) subscriber.getPublic())),
                    encoder.encodeToString(new byte[16]), LocalDateTime.of(2026, 1, 1, 0, 0));

            CloseableHttpAsyncClient client = mock(CloseableHttpAsyncClient.class);
            WebPushSubscriptionValidator validator = mock(WebPushSubscriptionValidator.class);
            HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 201, "Created");
            when(client.execute(any(HttpPost.class), isNull()))
                    .thenReturn(CompletableFuture.completedFuture(response));

            int status = new HttpWebPushSender(properties, client, validator)
                    .send(subscription, "{\"title\":\"test notification\"}");

            assertThat(status).isEqualTo(201);
            verify(validator).validateForDelivery(subscription);
            ArgumentCaptor<HttpPost> request = ArgumentCaptor.forClass(HttpPost.class);
            verify(client).execute(request.capture(), isNull());
            assertThat(request.getValue().getURI().toString()).isEqualTo(subscription.endpoint());
            assertThat(request.getValue().getFirstHeader("Authorization")).isNotNull();
            assertThat(request.getValue().getFirstHeader("Content-Encoding").getValue()).isEqualTo("aesgcm");
            assertThat(request.getValue().getEntity().getContentLength()).isPositive();
        } finally {
            if (!providerPreviouslyInstalled) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
            }
        }
    }
}
