package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
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
    private MockedStatic<SecurityUtils> mockedSecurityUtils;

    @BeforeEach
    void setUp() {
        adminUser = User.builder().build();
        ReflectionTestUtils.setField(adminUser, "userId", 1L);

        targetUser = User.builder().build();
        ReflectionTestUtils.setField(targetUser, "userId", 2L);

        admin = Admin.builder().user(adminUser).build();

        mockedSecurityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("사용자 제재 성공")
    void createSanction_success() {
        // given
        Long adminUserId = 1L;
        Long targetUserId = 2L;
        String type = "BAN";
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findByUserAndIsActive(adminUser, true)).thenReturn(Optional.of(admin));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type(type)
                .remark("Test")
                .startDate(java.time.LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 1L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        // when
        Long sanctionId = sanctionService.createSanction(adminUserId, targetUserId, type, "Test", null);

        // then
        assertThat(sanctionId).isNotNull();
        assertThat(targetUser.getStatus()).isEqualTo("SUSPENDED");
        verify(sanctionRepository).save(any(Sanction.class));
    }
}
