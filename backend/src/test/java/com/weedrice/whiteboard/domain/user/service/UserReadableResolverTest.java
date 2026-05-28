package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReadableResolverTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserReadableResolver userReadableResolver;

    @Test
    void resolve_existingUser_returnsUser() {
        Long userId = 1L;
        User user = User.builder().build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(userReadableResolver.resolve(userId)).isSameAs(user);
    }

    @Test
    void resolve_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userReadableResolver.resolve(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void resolveActive_existingActiveUser_returnsUser() {
        Long userId = 1L;
        User user = User.builder().build();
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(user));

        assertThat(userReadableResolver.resolveActive(userId)).isSameAs(user);
    }

    @Test
    void resolveActive_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        when(userRepository.findByUserIdAndStatusAndDeletedAtIsNull(userId, User.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userReadableResolver.resolveActive(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void ensureExists_existingUser_doesNotThrow() {
        Long userId = 1L;
        when(userRepository.existsById(userId)).thenReturn(true);

        assertThatCode(() -> userReadableResolver.ensureExists(userId))
                .doesNotThrowAnyException();
    }

    @Test
    void ensureExists_missingUser_throwsUserNotFound() {
        Long userId = 999L;
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> userReadableResolver.ensureExists(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
