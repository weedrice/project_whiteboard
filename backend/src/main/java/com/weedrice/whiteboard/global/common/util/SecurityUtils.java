package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    private static UserRepository staticUserRepository;
    private static AdminRepository staticAdminRepository;

    @PostConstruct
    public void init() {
        staticUserRepository = this.userRepository;
        staticAdminRepository = this.adminRepository;
    }

    public static Long getCurrentUserId() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ((CustomUserDetails) principal).getUserId();
        }
        
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    public static boolean isSuperAdmin() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(Role.ROLE_SUPER_ADMIN));
    }

    public static void validateBoardAdminPermission(Board board) {
        Long currentUserId = getCurrentUserId();
        User currentUser = staticUserRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean isSuperAdmin = "Y".equals(currentUser.getIsSuperAdmin());
        boolean isBoardAdmin = staticAdminRepository.findByUserAndBoardAndIsActive(currentUser, board, "Y").isPresent();
        boolean isCreator = board.getCreator().getUserId().equals(currentUser.getUserId());

        if (!isSuperAdmin && !isBoardAdmin && !isCreator) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public static void validateSuperAdminPermission() {
        Long currentUserId = getCurrentUserId();
        User currentUser = staticUserRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!"Y".equals(currentUser.getIsSuperAdmin())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
