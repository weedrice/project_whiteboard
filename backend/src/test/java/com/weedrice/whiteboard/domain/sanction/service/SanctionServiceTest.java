package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.sanction.dto.SanctionResponse;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserLifecycleService;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SanctionServiceTest {

    @Mock private SanctionRepository sanctionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ModerationActorResolver moderationActorResolver;
    @Mock private UserLifecycleService userLifecycleService;
    @Mock private SanctionPolicyService sanctionPolicyService;

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
        ReflectionTestUtils.setField(admin, "adminId", 10L);

        mockedSecurityUtils = mockStatic(SecurityUtils.class);
        lenient().when(moderationActorResolver.resolveActiveAdmin(1L)).thenReturn(admin);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    private PageRequest defaultSanctionPageable() {
        return PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("sanctionId")));
    }

    @Test
    @DisplayName("create sanction succeeds")
    void createSanction_success() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        when(moderationActorResolver.resolveActiveAdmin(1L)).thenReturn(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("BAN")
                .remark("Test")
                .startDate(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 1L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        Long sanctionId = sanctionService.createSanction(1L, 2L, "BAN", "Test", null, 100L, "post");

        assertThat(sanctionId).isEqualTo(1L);
        verify(userLifecycleService).suspendUser(targetUser);
        verify(sanctionRepository).save(argThat(sanction ->
                Long.valueOf(100L).equals(sanction.getContentId())
                        && "POST".equals(sanction.getContentType())));
    }

    @Test
    @DisplayName("temporary ban keeps user status active")
    void createSanction_temporaryBan_keepsUserStatusActive() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        when(moderationActorResolver.resolveActiveAdmin(1L)).thenReturn(admin);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("BAN")
                .remark("Temp ban")
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 2L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        sanctionService.createSanction(1L, 2L, "BAN", "Temp ban", LocalDateTime.now().plusDays(1), null, null);

        assertThat(targetUser.getStatus()).isEqualTo("ACTIVE");
        verify(userLifecycleService, never()).suspendUser(targetUser);
    }

    @Test
    @DisplayName("permanent ban rejects deleted users")
    void createSanction_permanentBanRejectsDeletedUser() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        targetUser.delete();
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE))
                .when(userLifecycleService).suspendUser(targetUser);

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "BAN", "Deleted user", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("create sanction normalizes type to uppercase")
    void createSanction_normalizesTypeToUpperCase() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        Sanction savedSanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("BAN")
                .remark("Temp ban")
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(savedSanction, "sanctionId", 3L);
        when(sanctionRepository.save(any(Sanction.class))).thenReturn(savedSanction);

        sanctionService.createSanction(1L, 2L, "ban", "Temp ban", LocalDateTime.now().plusDays(1), null, null);

        verify(sanctionRepository).save(argThat(sanction -> "BAN".equals(sanction.getType())));
    }

    @Test
    @DisplayName("reject BAN endDate when it is not in the future")
    void createSanction_rejectsPastOrImmediateBanEndDate() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "BAN", "Expired", LocalDateTime.now(), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("reject unsupported sanction type")
    void createSanction_rejectsUnsupportedType() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "BLOCK", "Invalid", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("reject incomplete sanction content metadata")
    void createSanction_rejectsIncompleteContentMetadata() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, null, "POST"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("reject unsupported sanction content type")
    void createSanction_rejectsUnsupportedContentType() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "WARNING", "Invalid", null, 100L, "ARTICLE"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("create sanction without admin throws forbidden")
    void createSanction_withoutAdmin_throwsForbidden() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        when(moderationActorResolver.resolveActiveAdmin(1L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> sanctionService.createSanction(1L, 2L, "BAN", "Test", null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("get sanctions returns mapped responses")
    void getSanctions_returnsMappedResponses() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);

        Sanction sanction = Sanction.builder()
                .targetUser(targetUser)
                .admin(admin)
                .type("BAN")
                .remark("Test")
                .startDate(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(sanction, "sanctionId", 1L);

        PageRequest pageable = PageRequest.of(0, 20);
        PageRequest safePageable = defaultSanctionPageable();
        when(sanctionRepository.findAll(safePageable))
                .thenReturn(new PageImpl<>(List.of(sanction), safePageable, 1));

        Page<SanctionResponse> responses = sanctionService.getSanctions(null, pageable);

        assertThat(responses.getContent()).hasSize(1);
        assertThat(responses.getContent().get(0).getSanctionId()).isEqualTo(1L);
        assertThat(responses.getContent().get(0).getAdminId()).isEqualTo(admin.getAdminId());
        verify(sanctionRepository).findAll(safePageable);
    }

    @Test
    @DisplayName("get sanctions uses stable default sort when pageable is unsorted")
    void getSanctions_appliesStableDefaultSort() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        PageRequest requestedPageable = PageRequest.of(0, 20);
        PageRequest safePageable = defaultSanctionPageable();
        when(sanctionRepository.findAll(safePageable))
                .thenReturn(new PageImpl<>(List.of(), safePageable, 0));

        sanctionService.getSanctions(null, requestedPageable);

        verify(sanctionRepository).findAll(safePageable);
    }

    @Test
    @DisplayName("get sanctions by target user uses stable default sort when pageable is unsorted")
    void getSanctionsByTargetUser_appliesStableDefaultSort() {
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        PageRequest requestedPageable = PageRequest.of(0, 20);
        PageRequest safePageable = defaultSanctionPageable();
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(sanctionRepository.findByTargetUser(targetUser, safePageable))
                .thenReturn(new PageImpl<>(List.of(), safePageable, 0));

        sanctionService.getSanctions(2L, requestedPageable);

        verify(sanctionRepository).findByTargetUser(targetUser, safePageable);
    }

    @Test
    @DisplayName("isUserBanned returns true when an active ban exists")
    void isUserBanned_trueWhenActiveBanExists() {
        when(sanctionPolicyService.isUserBanned(targetUser)).thenReturn(true);

        assertThat(sanctionService.isUserBanned(targetUser)).isTrue();
    }

    @Test
    @DisplayName("isUserMuted returns true when an active mute exists")
    void isUserMuted_trueWhenActiveMuteExists() {
        when(sanctionPolicyService.isUserMuted(targetUser)).thenReturn(true);

        assertThat(sanctionService.isUserMuted(targetUser)).isTrue();
    }

    @Test
    @DisplayName("inactive user is rejected by write validation")
    void validateNotBanned_rejectsInactiveUser() {
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionPolicyService).validateNotBanned(targetUser);

        assertThatThrownBy(() -> sanctionService.validateNotBanned(targetUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("muted user is rejected by write validation")
    void validateNotMuted_rejectsMutedUser() {
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(sanctionPolicyService).validateNotMuted(targetUser);

        assertThatThrownBy(() -> sanctionService.validateNotMuted(targetUser))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);
    }
}
