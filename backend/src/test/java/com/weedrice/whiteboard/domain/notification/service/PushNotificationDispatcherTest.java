package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationDispatcherTest {

    private static final LocalDateTime SNAPSHOT_AT = LocalDateTime.of(2026, 7, 17, 12, 0);

    @Mock
    private PushDispatchSnapshotReader snapshotReader;
    @Mock
    private PushSubscriptionCleanupService cleanupService;
    @Mock
    private WebPushSender webPushSender;

    private WebPushProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private PushNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new WebPushProperties();
        properties.setPublicKey("public-key");
        properties.setPrivateKey("private-key");
        properties.setSubject("mailto:ops@example.com");
        meterRegistry = new SimpleMeterRegistry();
        dispatcher = new PushNotificationDispatcher(
                properties,
                snapshotReader,
                cleanupService,
                webPushSender,
                new ObjectMapper(),
                meterRegistry);
    }

    @Test
    @DisplayName("push 전송은 snapshot을 사용하고 만료 구독을 전송 후 일괄 삭제한다")
    void dispatchSendsOutsideReaderAndDeletesExpiredSubscriptions() throws Exception {
        PushSubscriptionSnapshot active = snapshot(11L, "https://push/active");
        PushSubscriptionSnapshot expired = snapshot(12L, "https://push/expired");
        when(snapshotReader.loadEnabledSubscriptions(1L)).thenReturn(List.of(active, expired));
        when(webPushSender.send(eq(active), anyString())).thenReturn(201);
        when(webPushSender.send(eq(expired), anyString())).thenReturn(410);
        when(cleanupService.deleteExpiredSubscriptions(List.of(expired))).thenReturn(1);

        dispatcher.dispatch(new PushDispatchCommand(1L, 99L, "content", "like"));

        verify(cleanupService).deleteExpiredSubscriptions(List.of(expired));
        assertThat(meterRegistry.counter("noviis.webpush.delivery", "outcome", "success").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("noviis.webpush.delivery", "outcome", "expired").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("noviis.webpush.subscription.cleanup", "outcome", "deleted").count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("전송 timeout은 다음 구독 처리를 막지 않는다")
    void dispatchRecordsTimeoutAndContinues() throws Exception {
        PushSubscriptionSnapshot timedOut = snapshot(21L, "https://push/slow");
        PushSubscriptionSnapshot active = snapshot(22L, "https://push/active");
        when(snapshotReader.loadEnabledSubscriptions(1L)).thenReturn(List.of(timedOut, active));
        when(webPushSender.send(eq(timedOut), anyString()))
                .thenThrow(new WebPushDeliveryTimeoutException(new TimeoutException()));
        when(webPushSender.send(eq(active), anyString())).thenReturn(204);

        dispatcher.dispatch(new PushDispatchCommand(1L, 100L, "content", "comment"));

        verify(webPushSender).send(eq(active), anyString());
        verifyNoInteractions(cleanupService);
        assertThat(meterRegistry.counter("noviis.webpush.delivery", "outcome", "timeout").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("noviis.webpush.delivery", "outcome", "success").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("web push 설정이 비활성화되면 DB snapshot도 조회하지 않는다")
    void disabledPushSkipsDatabaseRead() {
        properties.setPrivateKey(null);

        dispatcher.dispatch(new PushDispatchCommand(1L, 100L, "content", "comment"));

        verifyNoInteractions(snapshotReader, cleanupService, webPushSender);
    }

    private PushSubscriptionSnapshot snapshot(Long subscriptionId, String endpoint) {
        return new PushSubscriptionSnapshot(subscriptionId, 1L, endpoint, "key", "auth", SNAPSHOT_AT);
    }
}
