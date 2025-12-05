package com.weedrice.whiteboard.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminCreateRequest {
    @NotNull
    private String loginId;
    private Long boardId; // 전체 관리자일 경우 null
    @NotBlank
    private String role; // BOARD_ADMIN, MODERATOR
}
