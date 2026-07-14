package com.weedrice.whiteboard.domain.emoticon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmoticonImageAddRequest {

    @NotNull(message = "{validation.file.id.required}")
    private Long fileId;
}
