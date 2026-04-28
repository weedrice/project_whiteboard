package com.weedrice.whiteboard.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAdminCommandService {

    private final UserLifecycleService userLifecycleService;

    @Transactional
    public void updateUserStatus(Long userId, String status) {
        userLifecycleService.updateAdminManagedStatus(userId, status);
    }
}
