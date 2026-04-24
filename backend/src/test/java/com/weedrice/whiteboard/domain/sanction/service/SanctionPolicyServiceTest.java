package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SanctionPolicyServiceTest {

    @Mock
    private SanctionRepository sanctionRepository;

    @InjectMocks
    private SanctionPolicyService sanctionPolicyService;

    @Test
    @DisplayName("isUserBanned returns true when an active ban exists")
    void isUserBanned_trueWhenActiveBanExists() {
        User user = activeUser();
        when(sanctionRepository.existsActiveBan(eq(user), any(LocalDateTime.class))).thenReturn(true);

        assertThat(sanctionPolicyService.isUserBanned(user)).isTrue();
    }

    @Test
    @DisplayName("isUserMuted returns true when an active mute exists")
    void isUserMuted_trueWhenActiveMuteExists() {
        User user = activeUser();
        when(sanctionRepository.existsActiveTypeIn(eq(user), eq(Set.of("MUTE")), any(LocalDateTime.class)))
                .thenReturn(true);

        assertThat(sanctionPolicyService.isUserMuted(user)).isTrue();
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
