package com.weedrice.whiteboard.domain.admin.dto;

import com.weedrice.whiteboard.domain.admin.entity.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminCreateRequest {
    @NotBlank
    private String loginId;

    @NotNull
    private Long boardId;

    @NotNull
    private AdminRole role;
}
