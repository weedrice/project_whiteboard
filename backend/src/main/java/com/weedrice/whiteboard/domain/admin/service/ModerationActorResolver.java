package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class ModerationActorResolver {
    private static final String ADMIN_ROLE_ADMIN = "ADMIN";
    private static final String ADMIN_ROLE_MODERATOR = "MODERATOR";

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
        return activeAdmins.stream()
                .max(Comparator
                        .comparingInt((Admin admin) -> rolePriority(admin.getRole()))
                        .thenComparing(Admin::getAdminId, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    public Admin resolveActiveAdmin(Long adminUserId) {
        return findActiveAdmin(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private int rolePriority(String role) {
        if (ADMIN_ROLE_ADMIN.equals(role)) {
            return 3;
        }
        if (Role.BOARD_ADMIN.equals(role)) {
            return 2;
        }
        if (ADMIN_ROLE_MODERATOR.equals(role)) {
            return 1;
        }
        return 0;
    }
}
