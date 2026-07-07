package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReadService {
    private static final int DEFAULT_ADMIN_PAGE_SIZE = 20;
    private static final Sort DEFAULT_ADMIN_SORT = Sort.by(Sort.Order.desc("adminId"));
    private static final Set<String> ALLOWED_ADMIN_SORTS = Set.of("adminId", "createdAt", "role", "isActive");

    private final AdminRepository adminRepository;

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public Page<AdminResponse> getAllAdmins(Pageable pageable) {
        Pageable safePageable = PageRequestUtils.of(
                pageable,
                DEFAULT_ADMIN_PAGE_SIZE,
                DEFAULT_ADMIN_SORT,
                ALLOWED_ADMIN_SORTS);
        Page<Admin> admins = adminRepository.findAll(safePageable);
        return admins.map(AdminResponse::from);
    }
}
