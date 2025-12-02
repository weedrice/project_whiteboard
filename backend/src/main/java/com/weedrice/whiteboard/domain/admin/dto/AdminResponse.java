package com.weedrice.whiteboard.domain.admin.dto;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class AdminResponse {
    private Long adminId;
    private UserInfo user;
    private BoardInfo board;
    private String role;
    private boolean isActive;
    private LocalDateTime createdAt;

    @Getter
    @Builder
    public static class UserInfo {
        private Long userId;
        private String loginId;
        private String displayName;
    }

    @Getter
    @Builder
    public static class BoardInfo {
        private Long boardId;
        private String boardName;
    }

    public static AdminResponse from(Admin admin) {
        UserInfo userInfo = UserInfo.builder()
                .userId(admin.getUser().getUserId())
                .loginId(admin.getUser().getLoginId())
                .displayName(admin.getUser().getDisplayName())
                .build();

        BoardInfo boardInfo = null;
        if (admin.getBoard() != null) {
            boardInfo = BoardInfo.builder()
                    .boardId(admin.getBoard().getBoardId())
                    .boardName(admin.getBoard().getBoardName())
                    .build();
        }

        return AdminResponse.builder()
                .adminId(admin.getAdminId())
                .user(userInfo)
                .board(boardInfo)
                .role(admin.getRole())
                .isActive("Y".equals(admin.getIsActive()))
                .createdAt(admin.getCreatedAt())
                .build();
    }

    public static List<AdminResponse> from(List<Admin> admins) {
        return admins.stream()
                .map(AdminResponse::from)
                .collect(Collectors.toList());
    }
}
