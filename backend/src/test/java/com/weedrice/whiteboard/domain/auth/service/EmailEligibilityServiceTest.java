package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailEligibilityServiceTest {

    @Mock
    private UserRepository userRepository;

    private EmailEligibilityService emailEligibilityService;

    @BeforeEach
    void setUp() {
        emailEligibilityService = new EmailEligibilityService(userRepository);
    }

    @Test
    void validateSignupEmail_allowsDeletedUserEmail() {
        User deletedUser = user("old@example.com", 1L, "DELETED");
        when(userRepository.findByEmail("old@example.com")).thenReturn(Optional.of(deletedUser));

        emailEligibilityService.validateSignupEmail(" old@example.com ");
    }

    @Test
    void validateSignupEmail_lowercasesEmailBeforeLookup() {
        emailEligibilityService.validateSignupEmail(" Old@Example.COM ");

        verify(userRepository).findByEmail("old@example.com");
    }

    @Test
    void validateSignupEmail_rejectsTooLongEmailBeforeRepositoryLookup() {
        assertThatThrownBy(() -> emailEligibilityService.validateSignupEmail("a".repeat(101)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void validateSignupEmail_rejectsNonDeletedUserEmail() {
        User activeUser = user("active@example.com", 1L, "ACTIVE");
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> emailEligibilityService.validateSignupEmail("active@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void validateChangeEmail_allowsCurrentUserEmail() {
        User currentUser = user("current@example.com", 1L, "ACTIVE");
        when(userRepository.findByEmail("current@example.com")).thenReturn(Optional.of(currentUser));

        emailEligibilityService.validateChangeEmail("current@example.com", 1L);
    }

    @Test
    void validateChangeEmail_rejectsOtherUserEmailRegardlessOfStatus() {
        User otherUser = user("other@example.com", 2L, "DELETED");
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> emailEligibilityService.validateChangeEmail("other@example.com", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void validateChangeEmail_rejectsMissingCurrentUserId() {
        assertThatThrownBy(() -> emailEligibilityService.validateChangeEmail("next@example.com", (Long) null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private User user(String email, Long userId, String status) {
        User user = User.builder()
                .email(email)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }
}
