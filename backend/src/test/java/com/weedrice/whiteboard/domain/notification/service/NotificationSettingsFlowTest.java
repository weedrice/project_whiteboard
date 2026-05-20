package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.dto.UpdateNotificationSettingItem;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettingsId;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsFlowTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserNotificationSettingsRepository userNotificationSettingsRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private SanctionService sanctionService;

    @Mock
    private EntityManager entityManager;

    private UserSettingsService userSettingsService;
    private NotificationService notificationService;

    private final Map<UserNotificationSettingsId, UserNotificationSettings> storedSettings = new LinkedHashMap<>();

    private User receiver;
    private User actor;

    @BeforeEach
    void setUp() {
        userSettingsService = new UserSettingsService(
                userRepository,
                userSettingsRepository,
                userNotificationSettingsRepository,
                entityManager,
                new UserWritableResolver(userRepository, sanctionService));
        NotificationPreferenceService preferenceService = new NotificationPreferenceService(userNotificationSettingsRepository);
        NotificationStreamService streamService = new NotificationStreamService(1_800_000L, 5);
        NotificationCommandService commandService = new NotificationCommandService(
                notificationRepository,
                preferenceService);
        NotificationEventHandler eventHandler = new NotificationEventHandler(commandService, streamService);
        NotificationQueryService queryService = new NotificationQueryService(notificationRepository);
        NotificationReadCommandService readCommandService =
                new NotificationReadCommandService(commandService);
        NotificationSseFacade sseFacade = new NotificationSseFacade(streamService);
        notificationService = new NotificationService(eventHandler, queryService, readCommandService, sseFacade,
                userRepository);

        receiver = User.builder().build();
        ReflectionTestUtils.setField(receiver, "userId", 1L);

        actor = User.builder().build();
        ReflectionTestUtils.setField(actor, "userId", 2L);
        ReflectionTestUtils.setField(actor, "displayName", "writer");

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(receiver));
        when(userNotificationSettingsRepository.findByUserIdOrderByModifiedAtDescCreatedAtDesc(1L))
                .thenAnswer(invocation -> new ArrayList<>(storedSettings.values()));
        when(userNotificationSettingsRepository.findByUserIdAndNotificationType(anyLong(), any(NotificationType.class)))
                .thenAnswer(invocation -> Optional.ofNullable(
                        storedSettings.get(new UserNotificationSettingsId(invocation.getArgument(0), invocation.getArgument(1)))));
        when(userNotificationSettingsRepository.saveAll(any()))
                .thenAnswer(invocation -> {
                    Iterable<UserNotificationSettings> settings = invocation.getArgument(0);
                    for (UserNotificationSettings setting : settings) {
                        storedSettings.put(
                                new UserNotificationSettingsId(setting.getUserId(), setting.getNotificationType()),
                                setting);
                    }
                    return settings;
                });
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Bulk notification settings affect subsequent notification delivery")
    void bulkNotificationSettings_affectNotificationDelivery() {
        userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem("LIKE", true),
                new UpdateNotificationSettingItem("COMMENT", false),
                new UpdateNotificationSettingItem("REPLY", true)));

        clearInvocations(notificationRepository);
        NotificationEvent commentEvent = new NotificationEvent(
                receiver, actor, NotificationType.COMMENT, "POST", 10L, "comment notification");
        notificationService.handleNotificationEvent(commentEvent);

        verify(notificationRepository, never()).save(any(Notification.class));

        userSettingsService.updateNotificationSettings(1L, List.of(
                new UpdateNotificationSettingItem("LIKE", true),
                new UpdateNotificationSettingItem("COMMENT", true),
                new UpdateNotificationSettingItem("REPLY", true)));

        clearInvocations(notificationRepository);

        notificationService.handleNotificationEvent(commentEvent);

        verify(notificationRepository).save(any(Notification.class));
        verify(userNotificationSettingsRepository, atLeastOnce())
                .findByUserIdAndNotificationType(eq(1L), eq(NotificationType.COMMENT));
    }
}
