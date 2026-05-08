package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private SecurityUtils securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils.init();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getCurrentUserId returns authenticated user id")
    void getCurrentUserId_success() {
        CustomUserDetails userDetails = new CustomUserDetails(1L, "user", "pw", Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getCurrentUserId throws when authentication is missing")
    void getCurrentUserId_unauthorized() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("isSuperAdmin returns true when role is present")
    void isSuperAdmin_true() {
        CustomUserDetails userDetails = new CustomUserDetails(
                1L,
                "admin",
                "pw",
                Collections.singletonList(new SimpleGrantedAuthority(Role.ROLE_SUPER_ADMIN)));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        assertThat(SecurityUtils.isSuperAdmin()).isTrue();
    }

    @Test
    @DisplayName("isSuperAdmin returns false when role is absent")
    void isSuperAdmin_false() {
        CustomUserDetails userDetails = new CustomUserDetails(
                1L,
                "user",
                "pw",
                Collections.singletonList(new SimpleGrantedAuthority(Role.ROLE_USER)));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        assertThat(SecurityUtils.isSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("validateSuperAdminPermission accepts usable super admin")
    void validateSuperAdminPermission_success() {
        setupSecurityContext(1L);
        User user = activeSuperAdmin(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        SecurityUtils.validateSuperAdminPermission();
    }

    @Test
    @DisplayName("validateSuperAdminPermission rejects suspended super admin")
    void validateSuperAdminPermission_rejectsSuspendedSuperAdmin() {
        setupSecurityContext(1L);
        User user = activeSuperAdmin(1L);
        user.suspend();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(SecurityUtils::validateSuperAdminPermission)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("validateBoardAdminPermission accepts usable super admin")
    void validateBoardAdminPermission_superAdmin() {
        setupSecurityContext(1L);
        User user = activeSuperAdmin(1L);
        Board board = boardWithCreator(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        SecurityUtils.validateBoardAdminPermission(board);
    }

    @Test
    @DisplayName("validateBoardAdminPermission rejects suspended super admin without board admin role")
    void validateBoardAdminPermission_rejectsSuspendedSuperAdmin() {
        setupSecurityContext(1L);
        User user = activeSuperAdmin(1L);
        user.suspend();
        Board board = boardWithCreator(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndBoardAndIsActive(eq(user), eq(board), eq(true))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> SecurityUtils.validateBoardAdminPermission(board))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("validateBoardAdminPermission rejects creator without active board admin role")
    void validateBoardAdminPermission_rejectsCreatorWithoutActiveBoardAdminRole() {
        setupSecurityContext(1L);
        User user = activeUser(1L);
        Board board = Board.builder().creator(user).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndBoardAndIsActive(eq(user), eq(board), eq(true))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> SecurityUtils.validateBoardAdminPermission(board))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("validateBoardAdminPermission accepts board admin")
    void validateBoardAdminPermission_boardAdmin() {
        setupSecurityContext(1L);
        User user = activeUser(1L);
        Board board = boardWithCreator(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndBoardAndIsActive(eq(user), eq(board), eq(true)))
                .thenReturn(Optional.of(Admin.builder().build()));

        SecurityUtils.validateBoardAdminPermission(board);
    }

    @Test
    @DisplayName("validateBoardAdminPermission rejects unrelated user")
    void validateBoardAdminPermission_forbidden() {
        setupSecurityContext(1L);
        User user = activeUser(1L);
        Board board = boardWithCreator(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndBoardAndIsActive(eq(user), eq(board), eq(true))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> SecurityUtils.validateBoardAdminPermission(board))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    private void setupSecurityContext(Long userId) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, "user", "pw", Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
    }

    private User activeUser(Long userId) {
        User user = User.builder()
                .loginId("user-" + userId)
                .password("pw")
                .email("user-" + userId + "@test.com")
                .displayName("user-" + userId)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private User activeSuperAdmin(Long userId) {
        User user = activeUser(userId);
        user.grantSuperAdminRole();
        return user;
    }

    private Board boardWithCreator(Long creatorId) {
        User creator = activeUser(creatorId);
        return Board.builder().creator(creator).build();
    }
}
