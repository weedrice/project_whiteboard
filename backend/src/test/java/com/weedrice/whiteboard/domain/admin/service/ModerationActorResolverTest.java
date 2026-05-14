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

import java.util.List;
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
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(adminUser, true))
                .thenReturn(List.of(admin));

        Admin resolved = moderationActorResolver.resolveActiveAdmin(1L);

        assertThat(resolved).isEqualTo(admin);
    }

    @Test
    @DisplayName("활성 관리자 행이 여러 개면 역할 우선순위와 최신 ID 기준으로 actor를 선택한다")
    void resolveActiveAdmin_prefersRolePriorityAndLatestAdminId() {
        Admin moderator = admin("MODERATOR", 30L);
        Admin olderBoardAdmin = admin(Role.BOARD_ADMIN, 20L);
        Admin latestBoardAdmin = admin(Role.BOARD_ADMIN, 40L);
        Admin unknownRole = admin("UNKNOWN", 50L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(adminUser, true))
                .thenReturn(List.of(moderator, olderBoardAdmin, latestBoardAdmin, unknownRole));

        Admin resolved = moderationActorResolver.resolveActiveAdmin(1L);

        assertThat(resolved).isEqualTo(latestBoardAdmin);
    }

    @Test
    @DisplayName("ADMIN 역할은 BOARD_ADMIN보다 actor 우선순위가 높다")
    void resolveActiveAdmin_prefersAdminRoleOverBoardAdmin() {
        Admin boardAdmin = admin(Role.BOARD_ADMIN, 50L);
        Admin adminRole = admin("ADMIN", 20L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(adminUser, true))
                .thenReturn(List.of(boardAdmin, adminRole));

        Admin resolved = moderationActorResolver.resolveActiveAdmin(1L);

        assertThat(resolved).isEqualTo(adminRole);
    }

    @Test
    @DisplayName("활성 관리자 행이 없으면 FORBIDDEN을 반환한다")
    void resolveActiveAdmin_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(adminUser, true))
                .thenReturn(List.of());

        assertThatThrownBy(() -> moderationActorResolver.resolveActiveAdmin(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("슈퍼 관리자는 활성 관리자 행 없이 moderation actor로 해석된다")
    void resolveModerationActor_withoutAdmin_success() {
        adminUser.grantSuperAdminRole();
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(adminUser, true))
                .thenReturn(List.of());

        ModerationActorResolver.ModerationActor actor = moderationActorResolver.resolveModerationActor(1L);

        assertThat(actor.user()).isEqualTo(adminUser);
        assertThat(actor.admin()).isNull();
    }

    @Test
    @DisplayName("사용 가능한 슈퍼 관리자가 아니면 moderation actor 해석을 거부한다")
    void resolveModerationActor_notUsableSuperAdmin_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> moderationActorResolver.resolveModerationActor(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    private Admin admin(String role, Long adminId) {
        Admin targetAdmin = Admin.builder()
                .user(adminUser)
                .board(admin.getBoard())
                .role(role)
                .build();
        ReflectionTestUtils.setField(targetAdmin, "adminId", adminId);
        return targetAdmin;
    }
}
