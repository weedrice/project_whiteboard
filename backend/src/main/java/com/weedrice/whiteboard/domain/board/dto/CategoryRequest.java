package com.weedrice.whiteboard.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CategoryRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Integer sortOrder;

    @Size(max = 20)
    @Pattern(regexp = "USER|BOARD_ADMIN|SUPER_ADMIN", message = "{validation.category.minWriteRole.pattern}")
    private String minWriteRole;
}
