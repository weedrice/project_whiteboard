package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
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
    private static final Set<String> ALLOWED_TYPES = Set.of("WARNING", "MUTE", "BAN");
    private static final int DEFAULT_SANCTION_PAGE_SIZE = 20;
    private static final Sort DEFAULT_SANCTION_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("sanctionId"));
    private static final Set<String> ALLOWED_SANCTION_SORTS = Set.of(
            "createdAt", "sanctionId", "type", "startDate", "endDate");

    private final SanctionRepository sanctionRepository;
    private final UserRepository userRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final UserLifecycleService userLifecycleService;
    private final SanctionPolicyService sanctionPolicyService;

    public SanctionService(SanctionRepository sanctionRepository,
                           UserRepository userRepository,
                           ModerationActorResolver moderationActorResolver,
                           UserLifecycleService userLifecycleService,
                           SanctionPolicyService sanctionPolicyService) {
        this.sanctionRepository = sanctionRepository;
        this.userRepository = userRepository;
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
        validateEndDate(endDate);

        Admin admin = moderationActorResolver.resolveActiveAdmin(adminUserId);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (isPermanentBan(normalizedType, endDate)) {
            userLifecycleService.suspendUser(targetUser);
        }

        Sanction sanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type(normalizedType)
                .remark(remark)
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

    private void validateEndDate(LocalDateTime endDate) {
        if (endDate != null && !endDate.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
