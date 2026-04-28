package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSecurityServiceTest {

    private UserSecurityService userSecurityService;

    @Mock private UserRepository userRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenLifecycleService refreshTokenLifecycleService;
    @Mock private SanctionService sanctionService;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        UserWritableResolver userWritableResolver = new UserWritableResolver(userRepository, sanctionService);
        userSecurityService = new UserSecurityService(
                userRepository,
                passwordHistoryRepository,
                passwordEncoder,
                refreshTokenLifecycleService,
                verificationCodeService,
                entityManager,
                userWritableResolver);
    }

    @Test
    @DisplayName("updatePassword stores history and revokes refresh tokens")
    void updatePassword_success() {
        User user = User.builder().password("encodedOld").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encodedOld")).thenReturn(true);
        when(passwordEncoder.matches("new", "encodedOld")).thenReturn(false);
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
        when(passwordEncoder.encode("new")).thenReturn("encodedNew");

        userSecurityService.updatePassword(1L, "old", "new");

        assertThat(user.getPassword()).isEqualTo("encodedNew");
        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
        verify(refreshTokenLifecycleService).revokeActiveRefreshTokens(user);
    }

    @Test
    @DisplayName("updatePassword rejects recently used passwords")
    void updatePassword_recentlyUsed() {
        User user = User.builder().password("encodedOld").build();
        PasswordHistory history = PasswordHistory.builder().passwordHash("encodedRecent").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encodedOld")).thenReturn(true);
        when(passwordEncoder.matches("new", "encodedOld")).thenReturn(false);
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(history));
        when(passwordEncoder.matches("new", "encodedRecent")).thenReturn(true);

        assertThatThrownBy(() -> userSecurityService.updatePassword(1L, "old", "new"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);
    }

    @Test
    @DisplayName("verifyAndChangeEmail consumes ticket after email save succeeds")
    void verifyAndChangeEmail_consumesTicketAfterSaveSuccess() {
        User user = User.builder().email("current@example.com").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("next@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        userSecurityService.verifyAndChangeEmail(1L, "next@example.com", "123456");

        assertThat(user.getEmail()).isEqualTo("next@example.com");
        var inOrder = inOrder(verificationCodeService, userRepository);
        inOrder.verify(verificationCodeService).validateVerificationTicket(
                "next@example.com",
                VerificationPurpose.CHANGE_EMAIL,
                "123456");
        inOrder.verify(userRepository).saveAndFlush(user);
        inOrder.verify(verificationCodeService).consumeValidatedVerificationTicket(
                "next@example.com",
                VerificationPurpose.CHANGE_EMAIL,
                "123456");
    }

    @Test
    @DisplayName("verifyAndChangeEmail maps flush conflicts to duplicate email")
    void verifyAndChangeEmail_duplicateEmailOnFlush() {
        User user = User.builder().email("current@example.com").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        User other = User.builder().email("next@example.com").build();
        ReflectionTestUtils.setField(other, "userId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("next@example.com"))
                .thenReturn(Optional.empty(), Optional.of(other));
        when(userRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatThrownBy(() -> userSecurityService.verifyAndChangeEmail(1L, "next@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

        verify(entityManager).clear();
        verify(verificationCodeService).validateVerificationTicket(
                "next@example.com",
                VerificationPurpose.CHANGE_EMAIL,
                "123456");
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(
                anyString(),
                any(),
                anyString());
    }
}
