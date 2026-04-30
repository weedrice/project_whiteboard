package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.dto.AdminUserCommentResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserDetailResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserPostResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserSubscriptionResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminSearchCondition;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.AdminUserDetailQueryRepository;
import com.weedrice.whiteboard.domain.user.repository.AdminUserDetailQueryRepository.AdminUserDetailStats;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminQueryService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AdminRepository adminRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final AdminUserDetailQueryRepository adminUserDetailQueryRepository;
    private final ModerationActorResolver moderationActorResolver;

    public Page<UserAdminResponse> searchUsersForAdmin(String keyword, Pageable pageable) {
        return searchUsersForAdmin(keyword, null, null, null, null, null,
                null, null, null, null, null, pageable);
    }

    public Page<UserAdminResponse> searchUsersForAdmin(
            String keyword,
            String status,
            String roleFilter,
            Boolean isEmailVerified,
            Boolean isSuperAdmin,
            Boolean isWithdrawn,
            LocalDate createdFromDate,
            LocalDate createdToDate,
            LocalDate lastLoginFromDate,
            LocalDate lastLoginToDate,
            Long minActivityCount,
            Pageable pageable) {
        UserAdminSearchCondition condition = UserAdminSearchCondition.builder()
                .status(status)
                .role(roleFilter)
                .isEmailVerified(isEmailVerified)
                .isSuperAdmin(isSuperAdmin)
                .isWithdrawn(isWithdrawn)
                .createdFrom(toStartOfDay(createdFromDate))
                .createdTo(toExclusiveEnd(createdToDate))
                .lastLoginFrom(toStartOfDay(lastLoginFromDate))
                .lastLoginTo(toExclusiveEnd(lastLoginToDate))
                .minActivityCount(minActivityCount)
                .build();

        Page<User> users = userRepository.searchUsersForAdmin(keyword, condition, pageable);
        Map<Long, String> rolesByUserId = resolveRolesForAdmin(users.getContent());
        List<UserAdminResponse> list = users.getContent().stream()
                .map(user -> UserAdminResponse.from(
                        user,
                        Boolean.TRUE.equals(user.getIsSuperAdmin())
                                ? Role.SUPER_ADMIN
                                : rolesByUserId.getOrDefault(user.getUserId(), Role.USER)))
                .toList();
        return new PageImpl<>(list, pageable, users.getTotalElements());
    }

    public AdminUserDetailResponse getUserDetailForAdmin(Long userId) {
        User user = getUserOrThrow(userId);

        String role = resolveRoleForAdmin(user);
        AdminUserDetailStats stats = adminUserDetailQueryRepository.findStats(user);

        return AdminUserDetailResponse.from(
                user,
                role,
                stats.postCount(),
                stats.commentCount(),
                stats.subscriptionCount(),
                stats.recentLogin(),
                stats.sanctionCount(),
                stats.recentSanction(),
                stats.reportTotalCount(),
                stats.reportPendingCount());
    }

    public Page<AdminUserPostResponse> getUserPostsForAdmin(Long userId, Pageable pageable) {
        User user = getUserOrThrow(userId);
        return postRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                .map(AdminUserPostResponse::from);
    }

    public Page<AdminUserCommentResponse> getUserCommentsForAdmin(Long userId, Pageable pageable) {
        User user = getUserOrThrow(userId);
        return commentRepository.findByUserOrderByCreatedAtDesc(user, pageable)
                .map(AdminUserCommentResponse::from);
    }

    public Page<AdminUserSubscriptionResponse> getUserSubscriptionsForAdmin(Long userId, Pageable pageable) {
        User user = getUserOrThrow(userId);
        Page<BoardSubscription> subscriptions = boardSubscriptionRepository.findByUserOrderBySortOrderAsc(user, pageable);
        Set<Long> activeAdminBoardIds = resolveActiveAdminBoardIds(user);

        return subscriptions
                .map(subscription -> AdminUserSubscriptionResponse.from(
                        subscription,
                        canReadSubscriptionBoard(subscription.getBoard(), user, activeAdminBoardIds)));
    }

    private String resolveRoleForAdmin(User user) {
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return Role.SUPER_ADMIN;
        }
        return moderationActorResolver.findActiveAdmin(user)
                .map(Admin::getRole)
                .orElse(Role.USER);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Map<Long, String> resolveRolesForAdmin(List<User> users) {
        List<Long> userIds = users.stream()
                .filter(user -> !Boolean.TRUE.equals(user.getIsSuperAdmin()))
                .map(User::getUserId)
                .toList();

        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return adminRepository.findByUserUserIdInAndIsActiveOrderByAdminIdAsc(userIds, true).stream()
                .collect(Collectors.toMap(
                        admin -> admin.getUser().getUserId(),
                        Admin::getRole,
                        (existingRole, ignoredRole) -> existingRole,
                        LinkedHashMap::new));
    }

    private Set<Long> resolveActiveAdminBoardIds(User user) {
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return Collections.emptySet();
        }
        return adminRepository.findActiveBoardIdsByUser(user).stream()
                .collect(Collectors.toSet());
    }

    private boolean canReadSubscriptionBoard(Board board, User user, Set<Long> activeAdminBoardIds) {
        if (board == null) {
            return false;
        }
        boolean hasBoardAdminAccess = hasBoardAdminAccess(board, user, activeAdminBoardIds);
        if (!Boolean.TRUE.equals(board.getIsActive()) && !hasBoardAdminAccess) {
            return false;
        }
        if (!Boolean.TRUE.equals(board.getIsPublic()) && !hasBoardAdminAccess) {
            return false;
        }
        return true;
    }

    private boolean hasBoardAdminAccess(Board board, User user, Set<Long> activeAdminBoardIds) {
        if (board == null || user == null) {
            return false;
        }
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return true;
        }
        if (board.getCreator() != null && Objects.equals(board.getCreator().getUserId(), user.getUserId())) {
            return true;
        }
        return activeAdminBoardIds.contains(board.getBoardId());
    }

    private LocalDateTime toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime toExclusiveEnd(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }
}
