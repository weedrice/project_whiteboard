package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserWritableResolverTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SanctionService sanctionService;

    @Test
    void resolve_rejectsInactiveUserBeforeBanCheck() {
        User user = User.builder()
                .loginId("user")
                .password("password")
                .email("user@test.com")
                .displayName("User")
                .build();
        user.suspend();
        UserWritableResolver resolver = new UserWritableResolver(userRepository, sanctionService);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> resolver.resolve(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(sanctionService, never()).validateNotBanned(user);
    }

    @Test
    void resolveForUpdate_rejectsDeletedUserBeforeBanCheck() {
        User user = User.builder()
                .loginId("user")
                .password("password")
                .email("user@test.com")
                .displayName("User")
                .build();
        user.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));
        UserWritableResolver resolver = new UserWritableResolver(userRepository, sanctionService);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> resolver.resolveForUpdate(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(sanctionService, never()).validateNotBanned(user);
    }
}
