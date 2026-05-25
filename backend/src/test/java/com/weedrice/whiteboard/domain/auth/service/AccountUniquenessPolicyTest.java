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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountUniquenessPolicyTest {

    @Mock
    private UserRepository userRepository;

    private AccountUniquenessPolicy accountUniquenessPolicy;

    @BeforeEach
    void setUp() {
        accountUniquenessPolicy = new AccountUniquenessPolicy(userRepository);
    }

    @Test
    void findReregisterableSignupUser_allowsDeletedUserEmail() {
        User deletedUser = user("old@example.com", 1L, "DELETED");
        when(userRepository.findByEmail("old@example.com")).thenReturn(Optional.of(deletedUser));

        Optional<User> result = accountUniquenessPolicy.findReregisterableSignupUser(" Old@Example.COM ");

        assertThat(result).containsSame(deletedUser);
    }

    @Test
    void findReregisterableSignupUser_rejectsNonDeletedUserEmail() {
        User activeUser = user("active@example.com", 1L, "ACTIVE");
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> accountUniquenessPolicy.findReregisterableSignupUser("active@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void validateLoginIdAvailable_rejectsDuplicateLoginId() {
        when(userRepository.existsByLoginId("testuser")).thenReturn(true);

        assertThatThrownBy(() -> accountUniquenessPolicy.validateLoginIdAvailable("testuser"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
    }

    @Test
    void resolveSignupConflict_prefersEmailThenLoginId() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("duplicate");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user("test@example.com", 1L, "ACTIVE")));

        RuntimeException result = accountUniquenessPolicy.resolveSignupConflict("test@example.com", "testuser", original);

        assertThat(result)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void resolveSignupConflict_mapsLoginIdWhenEmailIsAvailable() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("duplicate");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByLoginId("testuser")).thenReturn(true);

        RuntimeException result = accountUniquenessPolicy.resolveSignupConflict("test@example.com", "testuser", original);

        assertThat(result)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
    }

    @Test
    void validateChangeEmailAvailable_allowsCurrentUserEmail() {
        User currentUser = user("current@example.com", 1L, "ACTIVE");
        when(userRepository.findByEmail("current@example.com")).thenReturn(Optional.of(currentUser));

        accountUniquenessPolicy.validateChangeEmailAvailable("current@example.com", 1L);
    }

    @Test
    void validateChangeEmailAvailable_rejectsOtherUserEmailRegardlessOfStatus() {
        User otherUser = user("other@example.com", 2L, "DELETED");
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> accountUniquenessPolicy.validateChangeEmailAvailable("other@example.com", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void resolveChangeEmailConflict_returnsOriginalWhenEmailIsAvailable() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("other constraint");
        when(userRepository.findByEmail("next@example.com")).thenReturn(Optional.empty());

        RuntimeException result = accountUniquenessPolicy.resolveChangeEmailConflict("next@example.com", 1L, original);

        assertThat(result).isSameAs(original);
    }

    @Test
    void resolveChangeEmailConflict_mapsOtherUserEmailToDuplicateEmail() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("duplicate email");
        when(userRepository.findByEmail("next@example.com"))
                .thenReturn(Optional.of(user("next@example.com", 2L, User.STATUS_DELETED)));

        RuntimeException result = accountUniquenessPolicy.resolveChangeEmailConflict("next@example.com", 1L, original);

        assertThat(result)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
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
