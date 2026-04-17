package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.agent.service.AgentService;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.dto.AdminUserDetailResponse;
import com.weedrice.whiteboard.domain.user.dto.MyInfoResponse;
import com.weedrice.whiteboard.domain.user.dto.UpdateProfileResponse;
import com.weedrice.whiteboard.domain.user.dto.UserProfileResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminSearchCondition;
import com.weedrice.whiteboard.domain.user.entity.DisplayNameHistory;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.DisplayNameHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
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
public class UserService {

    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final DisplayNameHistoryRepository displayNameHistoryRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSettingsService userSettingsService;
    private final PostRepository postRepository;
    private final FileService fileService;
    private final com.weedrice.whiteboard.domain.point.repository.UserPointRepository userPointRepository;
    private final AdminRepository adminRepository;
    private final PostService postService;
    private final CommentService commentService;
    private final BoardService boardService;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SanctionRepository sanctionRepository;
    private final SanctionService sanctionService;
    private final ReportRepository reportRepository;
    private final VerificationCodeService verificationCodeService; // Inject VerificationCodeService
    private final AgentService agentService;
    private final ModerationActorResolver moderationActorResolver;
    private final EntityManager entityManager;

    public UserService(UserRepository userRepository,
                       CommentRepository commentRepository,
                       DisplayNameHistoryRepository displayNameHistoryRepository,
                       PasswordHistoryRepository passwordHistoryRepository,
                       PasswordEncoder passwordEncoder,
                       UserSettingsService userSettingsService,
                       PostRepository postRepository,
                       FileService fileService,
                       com.weedrice.whiteboard.domain.point.repository.UserPointRepository userPointRepository,
                       AdminRepository adminRepository,
                       PostService postService,
                       CommentService commentService,
                       BoardService boardService,
                       BoardSubscriptionRepository boardSubscriptionRepository,
                       LoginHistoryRepository loginHistoryRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       SanctionRepository sanctionRepository,
                       SanctionService sanctionService,
                       ReportRepository reportRepository,
                       VerificationCodeService verificationCodeService,
                       AgentService agentService,
                       EntityManager entityManager) {
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.displayNameHistoryRepository = displayNameHistoryRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.userSettingsService = userSettingsService;
        this.postRepository = postRepository;
        this.fileService = fileService;
        this.userPointRepository = userPointRepository;
        this.adminRepository = adminRepository;
        this.postService = postService;
        this.commentService = commentService;
        this.boardService = boardService;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sanctionRepository = sanctionRepository;
        this.sanctionService = sanctionService;
        this.reportRepository = reportRepository;
        this.verificationCodeService = verificationCodeService;
        this.agentService = agentService;
        this.moderationActorResolver = new ModerationActorResolver(userRepository, adminRepository);
        this.entityManager = entityManager;
    }

    public Long findUserIdByLoginId(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return user.getUserId();
    }

    public MyInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String role = user.getIsSuperAdmin() ? com.weedrice.whiteboard.domain.user.entity.Role.SUPER_ADMIN
                : com.weedrice.whiteboard.domain.user.entity.Role.USER;
        Integer points = userPointRepository.findById(user.getUserId())
                .map(com.weedrice.whiteboard.domain.point.entity.UserPoint::getCurrentPoint)
                .orElse(0);

        return MyInfoResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .status(user.getStatus())
                .role(role)
                .isEmailVerified(user.getIsEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .points(points)
                .build();
    }

    public UserProfileResponse getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long postCount = postRepository.countByUserAndIsDeleted(user, false);
        long commentCount = commentRepository.countByUser(user);

        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .postCount(postCount)
                .commentCount(commentCount)
                .build();
    }

    @Transactional
    public UpdateProfileResponse updateMyProfile(Long userId, String displayName, Long profileImageId) {
        User user = getWritableUser(userId);

        String oldDisplayName = user.getDisplayName();

        if (displayName != null && !displayName.equals(oldDisplayName)) {
            DisplayNameHistory history = DisplayNameHistory.builder()
                    .user(user)
                    .previousName(oldDisplayName)
                    .newName(displayName)
                    .build();
            displayNameHistoryRepository.save(history);

            user.updateDisplayName(displayName);
        }

        if (profileImageId != null) {
            fileService.associateFileWithEntity(profileImageId, userId, user.getUserId(), "USER_PROFILE");
            user.updateProfileImage(FileUrlResolver.resolve(profileImageId));
        }

        return new UpdateProfileResponse(user.getUserId(), user.getDisplayName(), user.getProfileImageUrl());
    }

    @Transactional
    public void updatePassword(Long userId, String currentPassword, String newPassword) {
        User user = getWritableUser(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
        }

        List<PasswordHistory> passwordHistories = passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user);
        for (PasswordHistory history : passwordHistories) {
            if (passwordEncoder.matches(newPassword, history.getPasswordHash())) {
                throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
            }
        }

        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.updatePassword(newPasswordHash);

        PasswordHistory history = PasswordHistory.builder()
                .user(user)
                .passwordHash(newPasswordHash)
                .build();
        passwordHistoryRepository.save(history);

        revokeActiveRefreshTokens(user);
    }

    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = getWritableUser(userId);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        agentService.suspendAllForUser(user);
        user.delete();
    }

    public Page<Comment> getMyComments(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return commentRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    public Page<User> searchUsers(String keyword, Pageable pageable) {
        return userRepository.searchUsers(keyword, pageable);
    }

    /**
     * 관리자용 사용자 검색. 각 사용자에 대해 역할(role)을 산출하여 UserAdminResponse로 반환한다.
     * SUPER_ADMIN &gt; BOARD_ADMIN/MODERATOR(활성 Admin 보유) &gt; USER 순으로 결정.
     */
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

        Optional<LoginHistory> recentLogin = loginHistoryRepository
                .findTopByUserAndIsSuccessTrueOrderByCreatedAtDesc(user);
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
        return boardService.getMySubscriptions(userId, pageable);
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
                        admin -> admin.getRole(),
                        (existingRole, ignoredRole) -> existingRole,
                        LinkedHashMap::new));
    }

    private LocalDateTime toStartOfDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay();
    }

    private LocalDateTime toExclusiveEnd(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.plusDays(1).atStartOfDay();
    }

    @Transactional
    public void updateUserStatus(Long userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if ("SUSPENDED".equals(status)) {
            user.suspend();
        } else if ("ACTIVE".equals(status)) {
            if (sanctionService.isUserBanned(user)) {
                throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
            }
            user.activate();
        } else {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Transactional
    public UserSettings updateSettings(Long userId, String theme, String language, String timezone, boolean hideNsfw) {
        getWritableUser(userId);
        return userSettingsService.updateSettingsEntity(userId, theme, language, timezone, hideNsfw);
    }

    @Transactional
    public void verifyAndChangeEmail(Long userId, String email, String code) {
        User user = getWritableUser(userId);

        // 본인 계정이 아닌 다른 계정이 이미 사용 중인 이메일은 변경할 수 없다.
        if (!user.getEmail().equals(email)) {
            userRepository.findByEmail(email).ifPresent(other -> {
                if (!other.getUserId().equals(user.getUserId())) {
                    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
                }
            });
        }

        // 코드 검증
        verificationCodeService.verifyCode(email, code);

        // 이메일 변경 및 인증 상태 업데이트
        if (!user.getEmail().equals(email)) {
            user.updateEmail(email);
        }

        user.verifyEmail();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            entityManager.clear();
            if (userRepository.findByEmail(email)
                    .filter(other -> !other.getUserId().equals(user.getUserId()))
                    .isPresent()) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            throw ex;
        }
    }

    public UserSettings getSettings(Long userId) {
        return userSettingsService.getOrCreateSettingsEntity(userId);
    }

    private void revokeActiveRefreshTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndIsRevoked(user, false);
        for (RefreshToken token : activeTokens) {
            token.revoke();
        }
        if (!activeTokens.isEmpty()) {
            refreshTokenRepository.saveAll(activeTokens);
        }
    }

    private User getWritableUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(user);
        return user;
    }
}
