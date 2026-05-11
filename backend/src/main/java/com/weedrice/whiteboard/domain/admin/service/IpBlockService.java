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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class IpBlockService {
    private static final int MAX_IP_ADDRESS_LENGTH = 45;
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
        String normalizedIpAddress = normalizeIpAddress(ipAddress);

        if (endDate != null && !endDate.isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "endDate must be in the future");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "reason must be 255 characters or less");
        }

        IpBlock ipBlock = ipBlockRepository.findByIdForUpdate(normalizedIpAddress)
                .map(existingIpBlock -> {
                    if (existingIpBlock.isActiveAt(now)) {
                        throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
                    }
                    existingIpBlock.reactivate(admin, reason, now, endDate);
                    return existingIpBlock;
                })
                .orElseGet(() -> IpBlock.builder()
                        .ipAddress(normalizedIpAddress)
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
        String normalizedIpAddress = normalizeIpAddress(ipAddress);
        IpBlock ipBlock = ipBlockRepository.findActiveByIpAddress(normalizedIpAddress, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ipBlock.expire(now);
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public Page<IpBlockResponse> getBlockedIps(Pageable pageable) {
        return ipBlockRepository.findActiveBlocks(LocalDateTime.now(), pageable)
                .map(IpBlockResponse::from);
    }

    public boolean isIpBlocked(String ipAddress) {
        String normalizedIpAddress = normalizeIpAddress(ipAddress);
        return ipBlockRepository.findActiveByIpAddress(normalizedIpAddress, LocalDateTime.now()).isPresent();
    }

    private String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ipAddress is required");
        }

        String normalizedIpAddress = ipAddress.trim();
        if (normalizedIpAddress.length() > MAX_IP_ADDRESS_LENGTH
                || containsWhitespace(normalizedIpAddress)
                || normalizedIpAddress.contains("/")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ipAddress is invalid");
        }
        if (!isIpv4Literal(normalizedIpAddress) && !isIpv6Literal(normalizedIpAddress)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ipAddress is invalid");
        }
        return normalizedIpAddress;
    }

    private boolean containsWhitespace(String value) {
        return value.chars().anyMatch(Character::isWhitespace);
    }

    private boolean isIpv4Literal(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int parsedPart;
            try {
                parsedPart = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (parsedPart < 0 || parsedPart > 255) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6Literal(String value) {
        if (!value.contains(":") || value.contains("%")) {
            return false;
        }
        try {
            return InetAddress.getByName(value) instanceof Inet6Address;
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
