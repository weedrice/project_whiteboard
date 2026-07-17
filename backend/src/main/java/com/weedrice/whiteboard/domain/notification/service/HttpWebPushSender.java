package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import lombok.RequiredArgsConstructor;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
class HttpWebPushSender implements WebPushSender {

    private final WebPushProperties properties;
    private final CloseableHttpAsyncClient webPushHttpClient;

    @Override
    public int send(PushSubscriptionSnapshot subscription, String payload) throws Exception {
        PushService pushService = new PushService(
                properties.getPublicKey(),
                properties.getPrivateKey(),
                properties.getSubject());
        HttpPost request = pushService.preparePost(new nl.martijndwars.webpush.Notification(
                subscription.endpoint(),
                subscription.p256dh(),
                subscription.auth(),
                payload), Encoding.AESGCM);
        Future<HttpResponse> future = webPushHttpClient.execute(request, null);
        HttpResponse response = null;
        try {
            response = future.get(properties.getResponseTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return response.getStatusLine().getStatusCode();
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new WebPushDeliveryTimeoutException(exception);
        } finally {
            if (response != null) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
    }
}
