package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ModerationActorResolver {
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    public ModerationActorResolver(UserRepository userRepository, AdminRepository adminRepository) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
    }

    public Optional<Admin> findActiveAdmin(Long adminUserId) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return findActiveAdmin(adminUser);
    }

    public Optional<Admin> findActiveAdmin(User adminUser) {
        if (adminUser == null) {
            return Optional.empty();
        }
        List<Admin> activeAdmins = adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(adminUser, true);
        return AdminRolePriority.selectHighestPriority(activeAdmins);
    }

    public ModerationActor resolveModerationActor(Long adminUserId) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!adminUser.isUsableSuperAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return new ModerationActor(adminUser, findActiveAdmin(adminUser).orElse(null));
    }

    public Admin resolveActiveAdmin(Long adminUserId) {
        return findActiveAdmin(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    public record ModerationActor(User user, Admin admin) {
    }

}
