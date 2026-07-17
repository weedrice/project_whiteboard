package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PushDispatchSnapshotReaderTest {

    @Mock PushSubscriptionRepository subscriptions;
    @Mock UserSettingsRepository settingsRepository;

    @Test
    void enabledPushLoadsOnlyDispatchableActiveUserSubscriptions() {
        UserSettingsRepository.SettingsReadProjection settings =
                mock(UserSettingsRepository.SettingsReadProjection.class);
        when(settings.getPushEnabled()).thenReturn(true);
        when(settingsRepository.findSettingsReadByUserId(1L)).thenReturn(Optional.of(settings));
        when(subscriptions.findDispatchableByUserId(1L)).thenReturn(List.of());
        PushDispatchSnapshotReader reader = new PushDispatchSnapshotReader(subscriptions, settingsRepository);

        assertThat(reader.loadEnabledSubscriptions(1L)).isEmpty();

        verify(subscriptions).findDispatchableByUserId(1L);
        verify(subscriptions, never()).findByUser_UserId(1L);
    }
}
