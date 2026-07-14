package com.weedrice.whiteboard.domain.emoticon.dto;

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
public class EmoticonUpdateRequest {

    @Size(max = 100, message = "{validation.emoticon.name.max}")
    private String name;

    private Long thumbnailFileId;

    @Size(max = 10, message = "{validation.emoticon.tags.max}")
    private List<String> tags;
}
