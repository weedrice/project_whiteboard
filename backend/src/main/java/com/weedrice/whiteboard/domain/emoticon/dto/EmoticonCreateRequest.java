package com.weedrice.whiteboard.domain.emoticon.dto;

import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
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

    @NotBlank(message = "{validation.emoticon.name.required}")
    @Size(max = 100, message = "{validation.emoticon.name.max}")
    private String name;

    private Long thumbnailFileId;

    @Size(max = 10, message = "{validation.emoticon.tags.max}")
    private List<String> tags;

    @Size(max = FileAssociationConstraints.MAX_EMOTICON_IMAGE_COUNT, message = "{validation.emoticon.images.max}")
    private List<Long> imageFileIds;
}
