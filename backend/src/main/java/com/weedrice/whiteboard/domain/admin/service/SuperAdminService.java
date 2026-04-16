package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.validation.constraints.NotNull;
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

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public SuperAdminUpdateResponse createSuperAdmin(@NotNull String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getIsSuperAdmin()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }

        user.grantSuperAdminRole();
        return SuperAdminUpdateResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    @Transactional
    public SuperAdminUpdateResponse deactivateSuperAdmin(@NotNull String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.getIsSuperAdmin()) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
        }

        user.revokeSuperAdminRole();
        return SuperAdminUpdateResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public List<SuperAdminResponse> getSuperAdmin() {
        return userRepository.findByIsSuperAdminTrue().stream()
                .map(SuperAdminResponse::from)
                .toList();
    }
}
