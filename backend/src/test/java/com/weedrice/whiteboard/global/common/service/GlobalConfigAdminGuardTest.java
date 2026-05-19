package com.weedrice.whiteboard.global.common.service;

import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalConfigAdminGuardTest {

    private final GlobalConfigAdminGuard adminGuard = new GlobalConfigAdminGuard();

    @Test
    @DisplayName("requireSuperAdmin delegates to SecurityUtils policy")
    void requireSuperAdmin_delegatesToSecurityUtils() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            adminGuard.requireSuperAdmin();

            utilities.verify(SecurityUtils::validateSuperAdminPermission);
        }
    }

    @Test
    @DisplayName("requireSuperAdmin propagates authorization failure")
    void requireSuperAdmin_propagatesAuthorizationFailure() {
        try (MockedStatic<SecurityUtils> utilities = Mockito.mockStatic(SecurityUtils.class)) {
            utilities.when(SecurityUtils::validateSuperAdminPermission)
                    .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

            assertThatThrownBy(adminGuard::requireSuperAdmin)
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        }
    }
}
