package com.weedrice.whiteboard.global.common.util;

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
import static org.mockito.Mockito.*;

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
        securityUtils.init(); // Initialize static fields
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("현재 사용자 ID 가져오기 성공")
    void getCurrentUserId_success() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(1L, "user", "pw", Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        // when
        Long userId = SecurityUtils.getCurrentUserId();

        // then
        assertThat(userId).isEqualTo(1L);
    }

    @Test
    @DisplayName("인증 정보가 없을 때 ID 가져오기 실패")
    void getCurrentUserId_unauthorized() {
        // given
        SecurityContextHolder.clearContext();

        // when & then
        assertThatThrownBy(SecurityUtils::getCurrentUserId)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("슈퍼 관리자 여부 확인 - True")
    void isSuperAdmin_true() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(1L, "admin", "pw", 
                Collections.singletonList(new SimpleGrantedAuthority(Role.ROLE_SUPER_ADMIN)));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        // when
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();

        // then
        assertThat(isSuperAdmin).isTrue();
    }

    @Test
    @DisplayName("슈퍼 관리자 여부 확인 - False")
    void isSuperAdmin_false() {
        // given
        CustomUserDetails userDetails = new CustomUserDetails(1L, "user", "pw", 
                Collections.singletonList(new SimpleGrantedAuthority(Role.ROLE_USER)));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        // when
        boolean isSuperAdmin = SecurityUtils.isSuperAdmin();

        // then
        assertThat(isSuperAdmin).isFalse();
    }

    @Test
    @DisplayName("슈퍼 관리자 권한 검증 성공")
    void validateSuperAdminPermission_success() {
        // given
        setupSecurityContext(1L);
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // when & then (no exception)
        SecurityUtils.validateSuperAdminPermission();
    }

    @Test
    @DisplayName("슈퍼 관리자 권한 검증 실패")
    void validateSuperAdminPermission_failure() {
        // given
        setupSecurityContext(1L);
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(SecurityUtils::validateSuperAdminPermission)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("게시판 관리자 권한 검증 성공 - 슈퍼 어드민")
    void validateBoardAdminPermission_superAdmin() {
        setupSecurityContext(1L);
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);
        
        Board board = Board.builder().creator(User.builder().build()).build();
        ReflectionTestUtils.setField(board.getCreator(), "userId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        SecurityUtils.validateBoardAdminPermission(board);
    }

    @Test
    @DisplayName("게시판 관리자 권한 검증 성공 - 게시판 생성자")
    void validateBoardAdminPermission_creator() {
        setupSecurityContext(1L);
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        
        Board board = Board.builder().creator(user).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // adminRepository is not called for creator check optimization? 
        // Logic: if (!isSuperAdmin && !isBoardAdmin && !isCreator)
        // isBoardAdmin will be checked.
        when(adminRepository.findByUserAndBoardAndIsActive(eq(user), eq(board), eq(true))).thenReturn(Optional.empty());

        SecurityUtils.validateBoardAdminPermission(board);
    }

    @Test
    @DisplayName("게시판 관리자 권한 검증 성공 - 게시판 관리자")
    void validateBoardAdminPermission_boardAdmin() {
        setupSecurityContext(1L);
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        
        User creator = User.builder().build();
        ReflectionTestUtils.setField(creator, "userId", 2L);
        Board board = Board.builder().creator(creator).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndBoardAndIsActive(eq(user), eq(board), eq(true))).thenReturn(Optional.of(com.weedrice.whiteboard.domain.admin.entity.Admin.builder().build()));

        SecurityUtils.validateBoardAdminPermission(board);
    }

    @Test
    @DisplayName("게시판 관리자 권한 검증 실패")
    void validateBoardAdminPermission_forbidden() {
        setupSecurityContext(1L);
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        
        User creator = User.builder().build();
        ReflectionTestUtils.setField(creator, "userId", 2L);
        Board board = Board.builder().creator(creator).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndBoardAndIsActive(eq(user), eq(board), eq(true))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> SecurityUtils.validateBoardAdminPermission(board))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    // Helper method
    private void setupSecurityContext(Long userId) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, "user", "pw", Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
    }
}
