package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.user.entity.Role;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AdminRolePriority {
    private static final String ADMIN_ROLE_ADMIN = "ADMIN";
    private static final String ADMIN_ROLE_MODERATOR = "MODERATOR";

    private AdminRolePriority() {
    }

    public static Optional<Admin> selectHighestPriority(List<Admin> admins) {
        return admins.stream()
                .max(Comparator
                        .comparingInt((Admin admin) -> priorityOf(admin.getRole()))
                        .thenComparing(Admin::getAdminId, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    private static int priorityOf(String role) {
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
