package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReadService {

    private final AdminRepository adminRepository;

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public Page<AdminResponse> getAllAdmins(Pageable pageable) {
        Page<Admin> admins = adminRepository.findAllByOrderByAdminIdDesc(pageable);
        return admins.map(AdminResponse::from);
    }
}
