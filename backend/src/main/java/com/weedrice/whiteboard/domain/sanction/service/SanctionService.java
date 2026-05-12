package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.sanction.dto.SanctionResponse;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserLifecycleService;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SanctionService {
    private static final String TYPE_BAN = "BAN";
    private static final int MAX_REMARK_LENGTH = 255;
    private static final Set<String> ALLOWED_TYPES = Set.of("WARNING", "MUTE", "BAN");
    private static final int DEFAULT_SANCTION_PAGE_SIZE = 20;
    private static final Sort DEFAULT_SANCTION_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("sanctionId"));
    private static final Set<String> ALLOWED_SANCTION_SORTS = Set.of(
            "createdAt", "sanctionId", "type", "startDate", "endDate");

    private final SanctionRepository sanctionRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final UserLifecycleService userLifecycleService;
    private final SanctionPolicyService sanctionPolicyService;

    public SanctionService(SanctionRepository sanctionRepository,
                           UserRepository userRepository,
                           PostRepository postRepository,
                           CommentRepository commentRepository,
                           ModerationActorResolver moderationActorResolver,
                           UserLifecycleService userLifecycleService,
                           SanctionPolicyService sanctionPolicyService) {
        this.sanctionRepository = sanctionRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.moderationActorResolver = moderationActorResolver;
        this.userLifecycleService = userLifecycleService;
        this.sanctionPolicyService = sanctionPolicyService;
    }

    @Transactional
    public Long createSanction(Long adminUserId, Long targetUserId, String type, String remark, LocalDateTime endDate,
                               Long contentId, String contentType) {
        SecurityUtils.validateSuperAdminPermission();
        String normalizedType = normalizeType(type);
        String normalizedContentType = normalizeContentType(contentId, contentType);
        String normalizedRemark = normalizeRemark(remark);
        validateEndDate(endDate);

        Admin admin = moderationActorResolver.resolveActiveAdmin(adminUserId);

        User targetUser = findSanctionTargetUser(targetUserId);
        validateSanctionContentTarget(contentId, normalizedContentType, targetUser);

        if (isPermanentBan(normalizedType, endDate)) {
            userLifecycleService.suspendUser(targetUser);
        }

        Sanction sanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type(normalizedType)
                .remark(normalizedRemark)
                .startDate(LocalDateTime.now())
                .endDate(endDate)
                .contentId(contentId)
                .contentType(normalizedContentType)
                .build();
        return sanctionRepository.save(sanction).getSanctionId();
    }

    public Page<SanctionResponse> getSanctions(Long targetUserId, Pageable pageable) {
        SecurityUtils.validateSuperAdminPermission();
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_SANCTION_PAGE_SIZE,
                DEFAULT_SANCTION_SORT,
                ALLOWED_SANCTION_SORTS);

        Page<Sanction> sanctions;
        if (targetUserId != null) {
            User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            sanctions = sanctionRepository.findByTargetUser(targetUser, safePageable);
        } else {
            sanctions = sanctionRepository.findAll(safePageable);
        }
        return sanctions.map(SanctionResponse::from);
    }

    public boolean isUserBanned(User user) {
        return sanctionPolicyService.isUserBanned(user);
    }

    public boolean isUserMuted(User user) {
        return sanctionPolicyService.isUserMuted(user);
    }

    public void validateNotBanned(User user) {
        sanctionPolicyService.validateNotBanned(user);
    }

    public void validateNotMuted(User user) {
        sanctionPolicyService.validateNotMuted(user);
    }

    private boolean isPermanentBan(String type, LocalDateTime endDate) {
        return TYPE_BAN.equalsIgnoreCase(type) && endDate == null;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(normalizedType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedType;
    }

    private String normalizeContentType(Long contentId, String contentType) {
        boolean hasContentId = contentId != null;
        boolean hasContentType = contentType != null && !contentType.isBlank();
        if (hasContentId != hasContentType) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!hasContentId) {
            return null;
        }
        if (contentId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            return ReportTargetType.from(contentType).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalizedRemark = remark.strip();
        if (normalizedRemark.length() > MAX_REMARK_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedRemark;
    }

    private User findSanctionTargetUser(Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        User targetUser = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!targetUser.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        return targetUser;
    }

    private void validateSanctionContentTarget(Long contentId, String normalizedContentType, User targetUser) {
        if (contentId == null) {
            return;
        }

        ReportTargetType targetType = ReportTargetType.valueOf(normalizedContentType);
        switch (targetType) {
            case POST -> validatePostTarget(contentId, targetUser);
            case COMMENT -> validateCommentTarget(contentId, targetUser);
            case USER -> validateUserContentTarget(contentId, targetUser);
        }
    }

    private void validatePostTarget(Long postId, User targetUser) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        validateSameTarget(post.getUser(), targetUser);
    }

    private void validateCommentTarget(Long commentId, User targetUser) {
        Comment comment = commentRepository.findNonDeletedByIdWithRelations(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (Boolean.TRUE.equals(comment.getPost().getIsDeleted())) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        validateSameTarget(comment.getUser(), targetUser);
    }

    private void validateUserContentTarget(Long userId, User targetUser) {
        User contentUser = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!contentUser.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        validateSameTarget(contentUser, targetUser);
    }

    private void validateSameTarget(User contentOwner, User targetUser) {
        if (!contentOwner.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
        if (!contentOwner.getUserId().equals(targetUser.getUserId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateEndDate(LocalDateTime endDate) {
        if (endDate != null && !endDate.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
