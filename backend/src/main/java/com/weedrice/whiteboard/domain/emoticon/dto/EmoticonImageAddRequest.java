package com.weedrice.whiteboard.domain.emoticon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmoticonImageAddRequest {

    @NotNull(message = "파일 ID는 필수입니다.")
    private Long fileId;
}
