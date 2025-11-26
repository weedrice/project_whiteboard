package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SanctionServiceTest {

    @Mock
    private SanctionRepository sanctionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private SanctionService sanctionService;

    private User adminUser;
    private User targetUser;
    private Admin admin;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().build();
        targetUser = User.builder().build();
        admin = Admin.builder().user(adminUser).build();
    }

    @Test
    @DisplayName("사용자 제재 성공")
    void createSanction_success() {
        // given
        Long adminUserId = 1L;
        Long targetUserId = 2L;
        String type = "BAN";
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findByUserAndIsActive(adminUser, "Y")).thenReturn(Optional.of(admin));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(sanctionRepository.save(any(Sanction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Sanction sanction = sanctionService.createSanction(adminUserId, targetUserId, type, "Test", null);

        // then
        assertThat(sanction.getType()).isEqualTo(type);
        assertThat(sanction.getTargetUser()).isEqualTo(targetUser);
        assertThat(targetUser.getStatus()).isEqualTo("SUSPENDED");
        verify(sanctionRepository).save(any(Sanction.class));
    }
}
