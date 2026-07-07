package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAccountEligibilityServiceTest {

    @Mock
    private SanctionPolicyService sanctionPolicyService;

    private LoginAccountEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new LoginAccountEligibilityService(sanctionPolicyService);
    }

    @Test
    @DisplayName("active account is login allowed")
    void evaluate_activeAccount_allowed() {
        User user = activeUser();

        LoginAccountEligibilityService.LoginAccountEligibility result = service.evaluate(user);

        assertThat(result.enabled()).isTrue();
        assertThat(result.accountNonLocked()).isTrue();
        assertThat(result.isLoginAllowed()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("deleted account is disabled without locked status")
    void evaluate_deletedAccount_disabled() {
        User user = activeUser();
        user.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));

        LoginAccountEligibilityService.LoginAccountEligibility result = service.evaluate(user);

        assertThat(result.enabled()).isFalse();
        assertThat(result.accountNonLocked()).isTrue();
        assertThat(result.isLoginAllowed()).isFalse();
        assertThat(result.failureReason())
                .isEqualTo(LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("banned active account is locked")
    void evaluate_bannedAccount_locked() {
        User user = activeUser();
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(true);

        LoginAccountEligibilityService.LoginAccountEligibility result = service.evaluate(user);

        assertThat(result.enabled()).isTrue();
        assertThat(result.accountNonLocked()).isFalse();
        assertThat(result.isLoginAllowed()).isFalse();
        assertThat(result.failureReason())
                .isEqualTo(LoginAccountEligibilityService.FAILURE_REASON_USER_BANNED);
    }

    @Test
    @DisplayName("missing account is disabled")
    void evaluate_missingAccount_disabled() {
        LoginAccountEligibilityService.LoginAccountEligibility result = service.evaluate(null);

        assertThat(result.enabled()).isFalse();
        assertThat(result.accountNonLocked()).isFalse();
        assertThat(result.isLoginAllowed()).isFalse();
        assertThat(result.failureReason())
                .isEqualTo(LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_FOUND);
    }

    private User activeUser() {
        return User.builder()
                .loginId("user")
                .email("user@test.com")
                .password("password")
                .displayName("User")
                .build();
    }
}
