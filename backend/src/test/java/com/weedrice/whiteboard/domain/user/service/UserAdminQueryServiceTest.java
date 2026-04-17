package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminQueryServiceTest {

    @InjectMocks
    private UserAdminQueryService userAdminQueryService;

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private ModerationActorResolver moderationActorResolver;
    @Mock private PostService postService;
    @Mock private CommentService commentService;
    @Mock private BoardService boardService;
    @Mock private BoardSubscriptionRepository boardSubscriptionRepository;
    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private SanctionRepository sanctionRepository;
    @Mock private SanctionService sanctionService;
    @Mock private ReportRepository reportRepository;

    @Test
    @DisplayName("관리자 사용자 검색은 역할을 일괄 조회한다")
    void searchUsersForAdmin_resolvesRolesInBatch() {
        User superAdmin = User.builder().loginId("super").email("super@test.com").password("pw").displayName("super").build();
        ReflectionTestUtils.setField(superAdmin, "userId", 1L);
        ReflectionTestUtils.setField(superAdmin, "isSuperAdmin", true);

        User moderator = User.builder().loginId("mod").email("mod@test.com").password("pw").displayName("mod").build();
        ReflectionTestUtils.setField(moderator, "userId", 2L);

        Page<User> page = new PageImpl<>(List.of(superAdmin, moderator), PageRequest.of(0, 20), 2);
        Admin activeAdmin = Admin.builder().user(moderator).board(null).role("MODERATOR").build();

        when(userRepository.searchUsersForAdmin(anyString(), any(), any())).thenReturn(page);
        when(adminRepository.findByUserUserIdInAndIsActiveOrderByAdminIdAsc(List.of(2L), true))
                .thenReturn(List.of(activeAdmin));

        Page<UserAdminResponse> response = userAdminQueryService.searchUsersForAdmin(
                "query", null, null, null, null, null,
                null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(response.getContent()).extracting(UserAdminResponse::getRole)
                .containsExactly("SUPER_ADMIN", "MODERATOR");
        verify(adminRepository).findByUserUserIdInAndIsActiveOrderByAdminIdAsc(List.of(2L), true);
    }

    @Test
    @DisplayName("정지된 사용자를 활성화할 때 BAN 상태면 거부한다")
    void updateUserStatus_activeBlockedByBan() {
        User user = User.builder().build();
        user.suspend();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sanctionService.isUserBanned(user)).thenReturn(true);

        assertThatThrownBy(() -> userAdminQueryService.updateUserStatus(1L, "ACTIVE"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("잘못된 상태값은 거부한다")
    void updateUserStatus_invalid() {
        User user = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userAdminQueryService.updateUserStatus(1L, "INVALID"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(sanctionService, never()).isUserBanned(user);
    }
}
