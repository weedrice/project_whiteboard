package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
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
    @Min(0)
    private Integer sortOrder;

    @Size(max = 20)
    @Pattern(regexp = "USER|BOARD_ADMIN|SUPER_ADMIN", message = "{validation.category.minWriteRole.pattern}")
    private String minWriteRole;

    @JsonProperty("isDefault")
    private Boolean isDefault;
}
