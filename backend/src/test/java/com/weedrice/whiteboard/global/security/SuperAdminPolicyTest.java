package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminPolicyTest {

    private static final Long ACTOR_USER_ID = 1L;

    private SuperAdminPolicy superAdminPolicy;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        superAdminPolicy = new SuperAdminPolicy(userRepository);
    }

    @Test
    @DisplayName("usable super admin is accepted")
    void requireUsableSuperAdmin_acceptsUsableSuperAdmin() {
        User user = user();
        user.grantSuperAdminRole();
        when(userRepository.findById(ACTOR_USER_ID)).thenReturn(Optional.of(user));

        superAdminPolicy.requireUsableSuperAdmin(ACTOR_USER_ID);

        verify(userRepository).findById(ACTOR_USER_ID);
    }

    @Test
    @DisplayName("null actor is rejected as unauthorized")
    void requireUsableSuperAdmin_rejectsNullActor() {
        assertThatThrownBy(() -> superAdminPolicy.requireUsableSuperAdmin(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("missing actor is rejected as user not found")
    void requireUsableSuperAdmin_rejectsMissingActor() {
        when(userRepository.findById(ACTOR_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> superAdminPolicy.requireUsableSuperAdmin(ACTOR_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("non super admin is rejected as forbidden")
    void requireUsableSuperAdmin_rejectsNonSuperAdmin() {
        when(userRepository.findById(ACTOR_USER_ID)).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> superAdminPolicy.requireUsableSuperAdmin(ACTOR_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("suspended super admin is rejected as forbidden")
    void requireUsableSuperAdmin_rejectsSuspendedSuperAdmin() {
        User user = user();
        user.grantSuperAdminRole();
        user.suspend();
        when(userRepository.findById(ACTOR_USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> superAdminPolicy.requireUsableSuperAdmin(ACTOR_USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    private User user() {
        return User.builder()
                .loginId("actor")
                .password("password")
                .email("actor@example.com")
                .displayName("Actor")
                .build();
    }
}
