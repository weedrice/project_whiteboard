package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
