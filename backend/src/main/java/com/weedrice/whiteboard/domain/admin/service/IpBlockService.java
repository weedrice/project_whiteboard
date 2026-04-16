package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.IpBlockResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IpBlockService {

    private final IpBlockRepository ipBlockRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public IpBlockResponse blockIp(Long adminUserId, String ipAddress, String reason, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Admin admin = adminRepository.findFirstByUserAndIsActiveOrderByAdminIdAsc(adminUser, true)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        if (endDate != null && !endDate.isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "endDate must be in the future");
        }

        if (ipBlockRepository.findActiveByIpAddress(ipAddress, now).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        IpBlock ipBlock = ipBlockRepository.findById(ipAddress)
                .map(existingIpBlock -> {
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

        return IpBlockResponse.from(ipBlockRepository.save(ipBlock));
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
