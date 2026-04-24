package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPrivilegeCleanupService {

    private final AdminRepository adminRepository;

    @Transactional
    public void removeOperationalPrivileges(User user) {
        Objects.requireNonNull(user, "user must not be null");

        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            user.revokeSuperAdminRole();
        }

        adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(user, true)
                .forEach(Admin::deactivate);
    }
}
