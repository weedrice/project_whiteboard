package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationActorResolverTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;

    private ModerationActorResolver moderationActorResolver;

    private User adminUser;
    private Admin admin;

    @BeforeEach
    void setUp() {
        moderationActorResolver = new ModerationActorResolver(userRepository, adminRepository);

        adminUser = User.builder()
                .loginId("admin")
                .displayName("Admin")
                .email("admin@test.com")
                .password("password")
                .build();
        ReflectionTestUtils.setField(adminUser, "userId", 1L);

        Board board = Board.builder()
                .boardName("board")
                .boardUrl("board")
                .creator(adminUser)
                .build();

        admin = Admin.builder()
                .user(adminUser)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "adminId", 10L);
    }

    @Test
    @DisplayName("활성 관리자 행을 공통 resolver에서 조회한다")
    void resolveActiveAdmin_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findFirstByUserAndIsActiveOrderByAdminIdAsc(adminUser, true))
                .thenReturn(Optional.of(admin));

        Admin resolved = moderationActorResolver.resolveActiveAdmin(1L);

        assertThat(resolved).isEqualTo(admin);
    }

    @Test
    @DisplayName("활성 관리자 행이 없으면 FORBIDDEN을 반환한다")
    void resolveActiveAdmin_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findFirstByUserAndIsActiveOrderByAdminIdAsc(adminUser, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> moderationActorResolver.resolveActiveAdmin(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }
}
