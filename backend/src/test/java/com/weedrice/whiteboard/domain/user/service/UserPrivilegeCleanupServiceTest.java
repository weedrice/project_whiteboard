package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPrivilegeCleanupServiceTest {

    @Mock private AdminRepository adminRepository;

    @InjectMocks
    private UserPrivilegeCleanupService userPrivilegeCleanupService;

    @Test
    void removeOperationalPrivileges_revokesSuperAdminAndDeactivatesActiveAdmins() {
        User user = User.builder().build();
        user.grantSuperAdminRole();
        Admin firstAdmin = Admin.builder().user(user).role("BOARD_ADMIN").build();
        Admin secondAdmin = Admin.builder().user(user).role("MODERATOR").build();
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(user, true))
                .thenReturn(List.of(firstAdmin, secondAdmin));

        userPrivilegeCleanupService.removeOperationalPrivileges(user);

        assertThat(user.getIsSuperAdmin()).isFalse();
        assertThat(firstAdmin.getIsActive()).isFalse();
        assertThat(secondAdmin.getIsActive()).isFalse();
        verify(adminRepository).findAllByUserAndIsActiveOrderByAdminIdAsc(user, true);
    }
}
