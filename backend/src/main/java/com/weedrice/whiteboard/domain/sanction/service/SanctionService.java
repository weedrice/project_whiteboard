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
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SanctionService {
    private static final String TYPE_BAN = "BAN";
    private static final String TYPE_MUTE = "MUTE";
    private static final Set<String> ALLOWED_TYPES = Set.of("WARNING", "MUTE", "BAN");

    private final SanctionRepository sanctionRepository;
    private final UserRepository userRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final UserLifecycleService userLifecycleService;

    public SanctionService(SanctionRepository sanctionRepository,
                           UserRepository userRepository,
                           ModerationActorResolver moderationActorResolver,
                           UserLifecycleService userLifecycleService) {
        this.sanctionRepository = sanctionRepository;
        this.userRepository = userRepository;
        this.moderationActorResolver = moderationActorResolver;
        this.userLifecycleService = userLifecycleService;
    }

    @Transactional
    public Long createSanction(Long adminUserId, Long targetUserId, String type, String remark, LocalDateTime endDate,
                               Long contentId, String contentType) {
        SecurityUtils.validateSuperAdminPermission();
        String normalizedType = normalizeType(type);
        String normalizedContentType = normalizeContentType(contentId, contentType);
        validateBanPeriod(normalizedType, endDate);

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

        Page<Sanction> sanctions;
        if (targetUserId != null) {
            User targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            sanctions = sanctionRepository.findByTargetUser(targetUser, pageable);
        } else {
            sanctions = sanctionRepository.findAll(pageable);
        }
        return sanctions.map(SanctionResponse::from);
    }

    public boolean isUserBanned(User user) {
        return user != null && sanctionRepository.existsActiveBan(user, LocalDateTime.now());
    }

    public boolean isUserMuted(User user) {
        return user != null && sanctionRepository.existsActiveTypeIn(user, Set.of(TYPE_MUTE), LocalDateTime.now());
    }

    public void validateNotBanned(User user) {
        if (user == null || !"ACTIVE".equals(user.getStatus()) || isUserBanned(user)) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }

    public void validateNotMuted(User user) {
        if (user == null || isUserMuted(user)) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
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

    private void validateBanPeriod(String type, LocalDateTime endDate) {
        if (TYPE_BAN.equals(type) && endDate != null && !endDate.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
