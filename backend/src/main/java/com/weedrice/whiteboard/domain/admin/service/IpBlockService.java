package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.IpBlockResponse;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.util.IpAddressCanonicalizer;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class IpBlockService {
    private static final int MAX_REASON_LENGTH = 255;
    private static final int DEFAULT_BLOCKED_IP_PAGE_SIZE = 20;
    private static final Sort DEFAULT_BLOCKED_IP_SORT = Sort.by(
            Sort.Order.desc("startDate"),
            Sort.Order.desc("ipAddress"));
    private static final Set<String> ALLOWED_BLOCKED_IP_SORTS = Set.of("startDate", "endDate", "ipAddress");

    private final IpBlockRepository ipBlockRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final Clock clock;

    public IpBlockService(IpBlockRepository ipBlockRepository,
                          ModerationActorResolver moderationActorResolver,
                          Clock clock) {
        this.ipBlockRepository = ipBlockRepository;
        this.moderationActorResolver = moderationActorResolver;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public IpBlockResponse blockIp(Long adminUserId, String ipAddress, String reason, LocalDateTime endDate) {
        LocalDateTime now = now();
        ModerationActorResolver.ModerationActor moderationActor =
                moderationActorResolver.resolveModerationActor(adminUserId);
        String normalizedIpAddress = normalizeIpAddress(ipAddress);

        if (endDate != null && !endDate.isAfter(now)) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.ipBlock.endDate.future");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.ipBlock.reason.size");
        }

        IpBlock ipBlock = ipBlockRepository.findByIdForUpdate(normalizedIpAddress)
                .map(existingIpBlock -> {
                    if (existingIpBlock.isActiveAt(now)) {
                        throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
                    }
                    existingIpBlock.reactivate(moderationActor.admin(), moderationActor.user(), reason, now, endDate);
                    return existingIpBlock;
                })
                .orElseGet(() -> IpBlock.builder()
                        .ipAddress(normalizedIpAddress)
                        .admin(moderationActor.admin())
                        .processorUser(moderationActor.user())
                        .reason(reason)
                        .startDate(now)
                        .endDate(endDate)
                        .build());

        try {
            return IpBlockResponse.from(ipBlockRepository.saveAndFlush(ipBlock));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public void unblockIp(String ipAddress) {
        LocalDateTime now = now();
        String normalizedIpAddress = normalizeIpAddress(ipAddress);
        IpBlock ipBlock = ipBlockRepository.findActiveByIpAddress(normalizedIpAddress, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ipBlock.expire(now);
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public Page<IpBlockResponse> getBlockedIps(Pageable pageable) {
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_BLOCKED_IP_PAGE_SIZE,
                DEFAULT_BLOCKED_IP_SORT,
                ALLOWED_BLOCKED_IP_SORTS);
        return ipBlockRepository.findActiveBlocks(now(), safePageable)
                .map(IpBlockResponse::from);
    }

    public boolean isIpBlocked(String ipAddress) {
        String normalizedIpAddress = normalizeIpAddress(ipAddress);
        return ipBlockRepository.findActiveByIpAddress(normalizedIpAddress, now()).isPresent();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.ipBlock.ipAddress.required");
        }
        return IpAddressCanonicalizer.canonicalize(ipAddress)
                .orElseThrow(() -> BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.ipBlock.ipAddress.invalid"));
    }
}
