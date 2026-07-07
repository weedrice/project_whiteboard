package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SanctionPolicyServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    @Mock
    private SanctionRepository sanctionRepository;

    private SanctionPolicyService sanctionPolicyService;

    @BeforeEach
    void setUp() {
        sanctionPolicyService = new SanctionPolicyService(sanctionRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("isUserBanned returns true when an active ban exists")
    void isUserBanned_trueWhenActiveBanExists() {
        User user = activeUser();
        when(sanctionRepository.existsActiveBan(eq(user), any(LocalDateTime.class))).thenReturn(true);

        assertThat(sanctionPolicyService.isUserBanned(user)).isTrue();
        verify(sanctionRepository).existsActiveBan(user, FIXED_NOW);
    }

    @Test
    @DisplayName("isUserMuted returns true when an active mute exists")
    void isUserMuted_trueWhenActiveMuteExists() {
        User user = activeUser();
        when(sanctionRepository.existsActiveTypeIn(eq(user), eq(Set.of("MUTE")), any(LocalDateTime.class)))
                .thenReturn(true);

        assertThat(sanctionPolicyService.isUserMuted(user)).isTrue();
        verify(sanctionRepository).existsActiveTypeIn(user, Set.of("MUTE"), FIXED_NOW);
    }

    @Test
    @DisplayName("inactive user is rejected by ban validation")
    void validateNotBanned_rejectsInactiveUser() {
        User user = activeUser();
        user.suspend();

        assertThatThrownBy(() -> sanctionPolicyService.validateNotBanned(user))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("muted user is rejected by mute validation")
    void validateNotMuted_rejectsMutedUser() {
        User user = activeUser();
        when(sanctionRepository.existsActiveTypeIn(eq(user), eq(Set.of("MUTE")), any(LocalDateTime.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> sanctionPolicyService.validateNotMuted(user))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    private User activeUser() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        return user;
    }
}
