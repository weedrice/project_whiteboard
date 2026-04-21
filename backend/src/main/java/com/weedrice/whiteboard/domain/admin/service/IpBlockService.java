package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.IpBlockResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class IpBlockService {
    private static final int MAX_REASON_LENGTH = 255;

    private final IpBlockRepository ipBlockRepository;
    private final ModerationActorResolver moderationActorResolver;

    public IpBlockService(IpBlockRepository ipBlockRepository,
                          ModerationActorResolver moderationActorResolver) {
        this.ipBlockRepository = ipBlockRepository;
        this.moderationActorResolver = moderationActorResolver;
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public IpBlockResponse blockIp(Long adminUserId, String ipAddress, String reason, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        Admin admin = moderationActorResolver.resolveActiveAdmin(adminUserId);

        if (endDate != null && !endDate.isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "endDate must be in the future");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reason must be 255 characters or less");
        }

        IpBlock ipBlock = ipBlockRepository.findByIdForUpdate(ipAddress)
                .map(existingIpBlock -> {
                    if (existingIpBlock.isActiveAt(now)) {
                        throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
                    }
                    existingIpBlock.reactivate(admin, reason, now, endDate);
                    return existingIpBlock;
                })
                .orElseGet(() -> IpBlock.builder()
                        .ipAddress(ipAddress)
                        .admin(admin)
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
        LocalDateTime now = LocalDateTime.now();
        IpBlock ipBlock = ipBlockRepository.findActiveByIpAddress(ipAddress, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ipBlock.expire(now);
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public Page<IpBlockResponse> getBlockedIps(Pageable pageable) {
        return ipBlockRepository.findActiveBlocks(LocalDateTime.now(), pageable)
                .map(IpBlockResponse::from);
    }

    public boolean isIpBlocked(String ipAddress) {
        return ipBlockRepository.findActiveByIpAddress(ipAddress, LocalDateTime.now()).isPresent();
    }
}
