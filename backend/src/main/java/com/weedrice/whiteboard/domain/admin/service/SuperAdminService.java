package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SuperAdminService {

    private final UserRepository userRepository;
    private final OperationalPrivilegeRevocationGuard privilegeRevocationGuard;

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public SuperAdminUpdateResponse createSuperAdmin(String loginId) {
        String normalizedLoginId = normalizeLoginId(loginId);
        User user = userRepository.findByLoginId(normalizedLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }

        user.grantSuperAdminRole();
        return SuperAdminUpdateResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public SuperAdminUpdateResponse deactivateSuperAdmin(String loginId, Long actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String normalizedLoginId = normalizeLoginId(loginId);
        User user = userRepository.findByLoginId(normalizedLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
        }
        privilegeRevocationGuard.validateSuperAdminCanBeRevokedBy(user, actorUserId);

        user.revokeSuperAdminRole();
        return SuperAdminUpdateResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public List<SuperAdminResponse> getSuperAdmin() {
        return getUsableSuperAdmins().stream()
                .map(SuperAdminResponse::from)
                .toList();
    }

    private List<User> getUsableSuperAdmins() {
        return userRepository.findUsableSuperAdmins();
    }

    private String normalizeLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return loginId.trim();
    }

}
