package com.weedrice.whiteboard.domain.emoticon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmoticonCreateRequest {

    @NotBlank(message = "이모티콘 이름을 입력해주세요.")
    @Size(max = 100, message = "이모티콘 이름은 100자 이하로 입력해주세요.")
    private String name;

    private String thumbnailUrl;

    @Size(max = 10, message = "태그는 최대 10개까지 입력할 수 있습니다.")
    private List<String> tags;

    private List<String> imageUrls;
}
