package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.dto.AdminUserDetailResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminSearchCondition;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserAdminQueryService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final AdminRepository adminRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final BoardService boardService;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final SanctionRepository sanctionRepository;
    private final SanctionService sanctionService;
    private final ReportRepository reportRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final AgentLifecycleService agentLifecycleService;

    public UserAdminQueryService(UserRepository userRepository,
                                 PostRepository postRepository,
                                 CommentRepository commentRepository,
                                 AdminRepository adminRepository,
                                 ModerationActorResolver moderationActorResolver,
                                 PostService postService,
                                 CommentService commentService,
                                 BoardService boardService,
                                 BoardSubscriptionRepository boardSubscriptionRepository,
                                 LoginHistoryRepository loginHistoryRepository,
                                 SanctionRepository sanctionRepository,
                                 SanctionService sanctionService,
                                 ReportRepository reportRepository,
                                 AgentLifecycleService agentLifecycleService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.adminRepository = adminRepository;
        this.postService = postService;
        this.commentService = commentService;
        this.boardService = boardService;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.sanctionRepository = sanctionRepository;
        this.sanctionService = sanctionService;
        this.reportRepository = reportRepository;
        this.moderationActorResolver = moderationActorResolver;
        this.agentLifecycleService = agentLifecycleService;
    }

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String role = resolveRoleForAdmin(user);
        long postCount = postRepository.countByUserAndIsDeleted(user, false);
        long commentCount = commentRepository.countByUserAndIsDeleted(user, false);
        long subscriptionCount = boardSubscriptionRepository.countByUser(user);
        Optional<LoginHistory> recentLogin = loginHistoryRepository.findTopByUserAndIsSuccessTrueOrderByCreatedAtDesc(user);
        long sanctionCount = sanctionRepository.countByTargetUser(user);
        Optional<Sanction> recentSanction = sanctionRepository.findTopByTargetUserOrderByCreatedAtDesc(user);
        long reportTotalCount = reportRepository.countByTargetTypeAndTargetId("USER", userId);
        long reportPendingCount = reportRepository.countByTargetTypeAndTargetIdAndStatus("USER", userId, "PENDING");

        return AdminUserDetailResponse.from(
                user,
                role,
                postCount,
                commentCount,
                subscriptionCount,
                recentLogin.orElse(null),
                sanctionCount,
                recentSanction.orElse(null),
                reportTotalCount,
                reportPendingCount);
    }

    public Page<PostSummary> getUserPostsForAdmin(Long userId, Pageable pageable) {
        return postService.getMyPosts(userId, pageable);
    }

    public Page<MyCommentResponse> getUserCommentsForAdmin(Long userId, Pageable pageable) {
        return commentService.getMyComments(userId, pageable);
    }

    public Page<BoardResponse> getUserSubscriptionsForAdmin(Long userId, Pageable pageable) {
        return boardService.getMySubscriptions(userId, pageable, true);
    }

    @Transactional
    public void updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if ("SUSPENDED".equals(status)) {
            if ("DELETED".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            user.suspend();
            agentLifecycleService.suspendAllForUser(user);
            return;
        }
        if ("ACTIVE".equals(status)) {
            if ("DELETED".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            if (sanctionService.isUserBanned(user)) {
                throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
            }
            user.activate();
            return;
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }

    private String resolveRoleForAdmin(User user) {
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            return Role.SUPER_ADMIN;
        }
        return moderationActorResolver.findActiveAdmin(user)
                .map(Admin::getRole)
                .orElse(Role.USER);
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

    private LocalDateTime toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime toExclusiveEnd(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }
}
