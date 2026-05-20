package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.sanction.dto.SanctionResponse;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SanctionService {
    private static final int DEFAULT_SANCTION_PAGE_SIZE = 20;
    private static final Sort DEFAULT_SANCTION_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("sanctionId"));
    private static final Set<String> ALLOWED_SANCTION_SORTS = Set.of(
            "createdAt", "sanctionId", "type", "startDate", "endDate");

    private final SanctionRepository sanctionRepository;
    private final UserRepository userRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final SanctionPolicyService sanctionPolicyService;
    private final SanctionRequestValidator sanctionRequestValidator;
    private final SanctionTargetResolver sanctionTargetResolver;
    private final SanctionEffectApplier sanctionEffectApplier;

    @Transactional
    public Long createSanction(Long adminUserId, Long targetUserId, String type, String remark, LocalDateTime endDate,
                                Long contentId, String contentType) {
        SanctionRequestValidator.NormalizedCommand command = sanctionRequestValidator.validate(
                type, remark, endDate, contentId, contentType);

        ModerationActorResolver.ModerationActor moderationActor =
                moderationActorResolver.resolveModerationActor(adminUserId);

        User targetUser = sanctionTargetResolver.resolveTargetUser(
                targetUserId, command.contentId(), command.contentType());
        sanctionEffectApplier.apply(command, targetUser);

        Sanction sanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(moderationActor.admin())
                .processorUser(moderationActor.user())
                .type(command.type())
                .remark(command.remark())
                .startDate(LocalDateTime.now())
                .endDate(command.endDate())
                .contentId(command.contentId())
                .contentType(command.contentType())
                .build();
        return sanctionRepository.save(sanction).getSanctionId();
    }

    public Page<SanctionResponse> getSanctions(Long targetUserId, Pageable pageable) {
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
}
