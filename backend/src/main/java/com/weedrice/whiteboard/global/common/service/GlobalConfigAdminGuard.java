package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import org.springframework.stereotype.Component;

@Component
public class GlobalConfigAdminGuard {

    public void requireSuperAdmin() {
        SecurityUtils.validateSuperAdminPermission();
    }
}
