package com.weedrice.whiteboard.domain.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAdminCommandServiceTest {

    @InjectMocks
    private UserAdminCommandService userAdminCommandService;

    @Mock
    private UserLifecycleService userLifecycleService;

    @Test
    @DisplayName("updateUserStatus delegates to lifecycle service")
    void updateUserStatus_delegatesToLifecycleService() {
        userAdminCommandService.updateUserStatus(1L, "SUSPENDED");

        verify(userLifecycleService).updateAdminManagedStatus(1L, "SUSPENDED");
    }
}
