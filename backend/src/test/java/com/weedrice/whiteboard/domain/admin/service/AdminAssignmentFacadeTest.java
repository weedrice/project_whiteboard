package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.AdminRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {
        AdminAssignmentFacade.class,
        AdminAssignmentFacadeTest.MethodSecurityTestConfig.class
})
class AdminAssignmentFacadeTest {

    @Autowired
    private AdminAssignmentService adminAssignmentService;

    @Autowired
    private AdminAssignmentFacade adminAssignmentFacade;

    @Test
    void classLevelPreAuthorize_requiresSuperAdmin() {
        PreAuthorize preAuthorize = AdminAssignmentFacade.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('SUPER_ADMIN')");
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void createAdmin_delegatesToService() {
        reset(adminAssignmentService);
        AdminResponse response = AdminResponse.builder()
                .adminId(1L)
                .role("BOARD_ADMIN")
                .isActive(true)
                .build();
        when(adminAssignmentService.createAdmin("admin", 1L, AdminRole.BOARD_ADMIN)).thenReturn(response);

        AdminResponse result = adminAssignmentFacade.createAdmin("admin", 1L, AdminRole.BOARD_ADMIN);

        assertThat(result).isSameAs(response);
    }

    @Test
    @WithMockUser(roles = "BOARD_ADMIN")
    void createAdmin_rejectsNonSuperAdminBeforeDelegation() {
        reset(adminAssignmentService);

        assertThatThrownBy(() -> adminAssignmentFacade.createAdmin("admin", 1L, AdminRole.BOARD_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void getBoardManager_delegatesToService() {
        reset(adminAssignmentService);

        adminAssignmentFacade.getBoardManager(1L);

        verify(adminAssignmentService).getBoardManager(1L);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void replaceBoardManager_delegatesToService() {
        reset(adminAssignmentService);

        adminAssignmentFacade.replaceBoardManager(1L, "admin");

        verify(adminAssignmentService).replaceBoardManager(1L, "admin");
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void deactivateAdmin_delegatesToService() {
        reset(adminAssignmentService);

        adminAssignmentFacade.deactivateAdmin(1L);

        verify(adminAssignmentService).deactivateAdmin(1L);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void activateAdmin_delegatesToService() {
        reset(adminAssignmentService);

        adminAssignmentFacade.activateAdmin(1L);

        verify(adminAssignmentService).activateAdmin(1L);
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        AdminAssignmentService adminAssignmentService() {
            return mock(AdminAssignmentService.class);
        }
    }
}
